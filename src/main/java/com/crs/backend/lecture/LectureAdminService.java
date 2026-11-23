package com.crs.backend.lecture;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.enrollment.EnrollmentService;
import com.crs.backend.lecture.dto.LectureCreateRequest;
import com.crs.backend.lecture.dto.LectureResponse;
import com.crs.backend.lecture.dto.LectureUpdateRequest;
import com.crs.backend.lecture.dto.TimeSlotRequest;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 강의 개설 관리(ACA-03). 처리 내용 ①~⑥ 순서를 따릅니다. */
@Service
public class LectureAdminService {

    private final LectureAdminRepository lectureAdminRepository;
    private final EnrollmentService enrollmentService;
    private final SchedulePolicy schedulePolicy;
    private final Clock clock;

    public LectureAdminService(
            LectureAdminRepository lectureAdminRepository,
            EnrollmentService enrollmentService,
            SchedulePolicy schedulePolicy,
            Clock clock) {
        this.lectureAdminRepository = lectureAdminRepository;
        this.enrollmentService = enrollmentService;
        this.schedulePolicy = schedulePolicy;
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
        checkUpdateOpen(current.semNo());
        List<LectureTimeSlot> slots = toSlots(request.timeSlots());
        checkSlots(slots);
        checkRoomAssigned(current.semNo(), slots);
        checkProfessor(request.profNo());

        boolean slotsChanged = changed(current.timeSlots(), slots, false);
        if (changed(current.timeSlots(), slots, true)) {
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
    // 신청 종료 전에만 해서, 신청이 취소된 학생이 남은 기간에 다른 강의를 신청할 수 있게 한다.
    // 수강신청과 같은 순서(강의 → 수강신청)로 잠그려고 강의 행부터 잠근다
    @Transactional
    public LectureResponse close(int lecNo) {
        lock(lecNo);
        LectureResponse current = get(lecNo);
        if (current.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (passed(schedulePolicy.registrationEnd(current.semNo()))) {
            throw new BusinessException(ErrorCode.CLOSE_PERIOD_OVER);
        }

        enrollmentService.clearClosedLecture(lecNo, now);
        lectureAdminRepository.close(lecNo);
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
        if (passed(schedulePolicy.cartStart(semNo))) {
            throw new BusinessException(ErrorCode.LECTURE_OPEN_CLOSED);
        }
    }

    /** 강의실·담당 교수·정원은 신청 종료(정정 종료, 정정이 없으면 마지막 수강신청일 종료) 전까지만 바꿀 수 있습니다. */
    private void checkUpdateOpen(int semNo) {
        if (passed(schedulePolicy.registrationEnd(semNo))) {
            throw new BusinessException(ErrorCode.UPDATE_PERIOD_OVER);
        }
    }

    /** 강의시간·수업유형은 수강신청 기간이 시작되기 전까지만 바꿀 수 있습니다. 신청한 학생의 시간표가 겹치지 않게 하려는 것입니다. */
    private void checkTimeChangeOpen(int semNo) {
        if (passed(schedulePolicy.enrollStart(semNo))) {
            throw new BusinessException(ErrorCode.TIME_CHANGE_CLOSED);
        }
    }

    /** 대면 수업은 정정 기간(개강 주)이 시작되기 전까지만 강의실을 미배정으로 둘 수 있습니다. 정정이 없는 학기는 마지막 수강신청일 종료까지입니다. */
    private void checkRoomAssigned(int semNo, List<LectureTimeSlot> slots) {
        boolean unassigned =
                slots.stream().anyMatch(slot -> slot.clsType() == ClsType.OFFLINE && slot.roomCd() == null);
        if (unassigned && passed(schedulePolicy.adjustStart(semNo).or(() -> schedulePolicy.registrationEnd(semNo)))) {
            throw new BusinessException(ErrorCode.ROOM_REQUIRED);
        }
    }

    /** 일정 시각이 지났는지. 일정이 아직 없으면 지나지 않은 것으로 봅니다. */
    private boolean passed(Optional<LocalDateTime> moment) {
        LocalDateTime now = LocalDateTime.now(clock);
        return moment.map(at -> !now.isBefore(at)).orElse(false);
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

    /** 강의시간 칸이 바뀌었는지 봅니다. ignoreRoom 이면 강의실만 바뀐 건 바뀌지 않은 것으로 봅니다. */
    private boolean changed(List<TimeSlotResponse> current, List<LectureTimeSlot> requested, boolean ignoreRoom) {
        var before = new HashSet<>(current.stream()
                .map(TimeSlotResponse::toSlot)
                .map(slot -> ignoreRoom ? withoutRoom(slot) : slot)
                .toList());
        var after = new HashSet<>(requested.stream()
                .map(slot -> ignoreRoom ? withoutRoom(slot) : slot)
                .toList());
        return !before.equals(after);
    }

    private static LectureTimeSlot withoutRoom(LectureTimeSlot slot) {
        return new LectureTimeSlot(slot.dow(), slot.startTm(), slot.endTm(), slot.clsType(), null);
    }
}
