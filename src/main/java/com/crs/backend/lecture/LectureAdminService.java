package com.crs.backend.lecture;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.lecture.dto.LectureCreateRequest;
import com.crs.backend.lecture.dto.LectureResponse;
import com.crs.backend.lecture.dto.LectureUpdateRequest;
import com.crs.backend.lecture.dto.TimeSlotRequest;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 강의 개설 관리(ACA-03). 처리 내용 ①~⑥ 순서를 따릅니다. */
@Service
public class LectureAdminService {

    private final LectureAdminRepository lectureAdminRepository;
    private final Clock clock;

    public LectureAdminService(LectureAdminRepository lectureAdminRepository, Clock clock) {
        this.lectureAdminRepository = lectureAdminRepository;
        this.clock = clock;
    }

    public List<LectureResponse> list(int semNo) {
        return lectureAdminRepository.findBySemester(semNo);
    }

    public LectureResponse get(int lecNo) {
        return lectureAdminRepository
                .findById(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
    }

    // ①~④ 개설
    @Transactional
    public LectureResponse create(LectureCreateRequest request) {
        List<LectureTimeSlot> slots = toSlots(request.timeSlots());
        checkSlots(slots);
        if (!lectureAdminRepository.courseExists(request.crsCd())) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!lectureAdminRepository.semesterExists(request.semNo())) {
            throw new BusinessException(ErrorCode.SEMESTER_NOT_FOUND);
        }
        checkProfessor(request.profNo());
        checkOpenPeriod(request.semNo());
        if (lectureAdminRepository.sectionExists(request.semNo(), request.crsCd(), request.sectNo())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SECTION);
        }
        checkConflicts(request.semNo(), 0, request.profNo(), slots);

        int lecNo = lectureAdminRepository.insertLecture(
                request.crsCd(),
                request.semNo(),
                request.profNo(),
                request.sectNo(),
                request.cap(),
                request.engLec(),
                request.relGrdExmp());
        lectureAdminRepository.replaceSlots(lecNo, slots);
        return get(lecNo);
    }

    // ⑤ 수정. 정원 비교가 동시에 들어온 신청과 엇갈리지 않게 강의 행부터 잠근다
    @Transactional
    public LectureResponse update(int lecNo, LectureUpdateRequest request) {
        lock(lecNo);
        LectureResponse current = get(lecNo);
        if (current.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        List<LectureTimeSlot> slots = toSlots(request.timeSlots());
        checkSlots(slots);
        checkProfessor(request.profNo());

        boolean slotsChanged = !sameSlots(current.timeSlots(), slots);
        if (slotsChanged) {
            checkTimeChangeOpen(current.semNo());
        }
        if (request.cap() < current.enrlCnt()) {
            throw new BusinessException(ErrorCode.CAPACITY_BELOW_ENROLLED);
        }
        checkConflicts(current.semNo(), lecNo, request.profNo(), slots);

        lectureAdminRepository.updateLecture(
                lecNo, request.profNo(), request.cap(), request.engLec(), request.relGrdExmp());
        if (slotsChanged) {
            lectureAdminRepository.replaceSlots(lecNo, slots);
        }
        return get(lecNo);
    }

    // ⑥ 폐강: 강의상태를 바꾸고, 신청을 모두 취소하고, 장바구니에서도 뺀다.
    // 수강신청과 같은 순서(강의 → 수강신청)로 잠그려고 강의 행부터 잠근다
    @Transactional
    public LectureResponse close(int lecNo) {
        lock(lecNo);
        LectureResponse current = get(lecNo);
        if (current.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean adjustOver = lectureAdminRepository
                .findAdjustEnd(current.semNo())
                .map(end -> !now.isBefore(end))
                .orElse(false);
        if (adjustOver) {
            throw new BusinessException(ErrorCode.CLOSE_PERIOD_OVER);
        }

        lectureAdminRepository.cancelEnrollments(lecNo, now);
        lectureAdminRepository.close(lecNo);
        lectureAdminRepository.deleteCarts(lecNo);
        return get(lecNo);
    }

    private List<LectureTimeSlot> toSlots(List<TimeSlotRequest> requests) {
        return requests.stream().map(TimeSlotRequest::toSlot).toList();
    }

    /** 입력만 보고 알 수 있는 오류: 시각 순서, 온라인 강의실, 강의 안의 칸끼리 겹침, 없는 강의실 */
    private void checkSlots(List<LectureTimeSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            LectureTimeSlot slot = slots.get(i);
            if (!slot.startTm().isBefore(slot.endTm())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시작시각은 종료시각보다 빨라야 합니다.");
            }
            if (slot.clsType() == ClsType.ONLINE && slot.roomCd() != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "온라인 수업에는 강의실을 지정하지 않습니다.");
            }
            for (int j = i + 1; j < slots.size(); j++) {
                if (slot.overlaps(slots.get(j))) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 강의의 강의시간끼리 겹칩니다.");
                }
            }
            if (slot.roomCd() != null && !lectureAdminRepository.classroomExists(slot.roomCd())) {
                throw new BusinessException(ErrorCode.CLASSROOM_NOT_FOUND);
            }
        }
    }

    private void checkProfessor(String profNo) {
        if (!lectureAdminRepository.activeProfessorExists(profNo)) {
            throw new BusinessException(ErrorCode.PROFESSOR_NOT_FOUND);
        }
    }

    private void lock(int lecNo) {
        if (!lectureAdminRepository.lockLecture(lecNo)) {
            throw new BusinessException(ErrorCode.LECTURE_NOT_FOUND);
        }
    }

    /** 새 강의는 장바구니 기간이 시작되기 전까지만 개설합니다(처리 시기). */
    private void checkOpenPeriod(int semNo) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean started = lectureAdminRepository
                .findCartStart(semNo)
                .map(start -> !now.isBefore(start))
                .orElse(false);
        if (started) {
            throw new BusinessException(ErrorCode.LECTURE_OPEN_CLOSED);
        }
    }

    /** 기존 강의의 강의시간·강의실은 수강신청 기간이 시작되기 전까지만 바꿀 수 있습니다. */
    private void checkTimeChangeOpen(int semNo) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean started = lectureAdminRepository
                .findEnrollStart(semNo)
                .map(start -> !now.isBefore(start))
                .orElse(false);
        if (started) {
            throw new BusinessException(ErrorCode.TIME_CHANGE_CLOSED);
        }
    }

    // ③ 강의실이 지정된 칸은 강의실 중복을, 모든 칸은 교수 시간 중복을 본다
    private void checkConflicts(int semNo, int exceptLecNo, String profNo, List<LectureTimeSlot> slots) {
        for (LectureTimeSlot slot : slots) {
            if (slot.roomCd() != null) {
                lectureAdminRepository
                        .findRoomConflict(semNo, exceptLecNo, slot)
                        .ifPresent(other -> {
                            throw new BusinessException(ErrorCode.ROOM_TIME_CONFLICT, other + " 강의와 강의실 사용 시간이 겹칩니다.");
                        });
            }
            lectureAdminRepository
                    .findProfessorConflict(semNo, exceptLecNo, profNo, slot)
                    .ifPresent(other -> {
                        throw new BusinessException(
                                ErrorCode.PROFESSOR_TIME_CONFLICT, "담당 교수의 " + other + " 강의와 시간이 겹칩니다.");
                    });
        }
    }

    private boolean sameSlots(List<TimeSlotResponse> current, List<LectureTimeSlot> requested) {
        var before =
                new HashSet<>(current.stream().map(TimeSlotResponse::toSlot).toList());
        return before.size() == requested.size() && before.containsAll(requested);
    }
}
