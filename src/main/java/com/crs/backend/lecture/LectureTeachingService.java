package com.crs.backend.lecture;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.lecture.LectureTeachingRepository.SemesterRow;
import com.crs.backend.lecture.LectureTeachingRepository.TeachingLectureRow;
import com.crs.backend.lecture.LectureTeachingRepository.TimeSlotRow;
import com.crs.backend.lecture.dto.TeachingLectureItem;
import com.crs.backend.lecture.dto.TeachingLectureListResponse;
import com.crs.backend.lecture.dto.TeachingTimeSlot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 담당 강의 조회(LEC-01). 교수는 담당 강의, 조교(학생)는 배정된 강의를 봅니다. */
@Service
public class LectureTeachingService {

    private static final String PLACE_ONLINE = "온라인";
    private static final String PLACE_UNASSIGNED = "미배정";

    private final LectureTeachingRepository lectureTeachingRepository;

    public LectureTeachingService(LectureTeachingRepository lectureTeachingRepository) {
        this.lectureTeachingRepository = lectureTeachingRepository;
    }

    /** semNo 가 null 이면 현재 학기를 봅니다. 없는 학기이거나 현재 학기가 지정되지 않았으면 404 SEMESTER_NOT_FOUND 입니다. */
    @Transactional(readOnly = true)
    public TeachingLectureListResponse list(CurrentUser user, Integer semNo) {
        // 관리자는 담당 강의가 없다(처리 주체는 교수·조교)
        if (user.role() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 조회할 학기: 지정한 학기, 없으면 현재 학기
        Optional<SemesterRow> semester = semNo == null
                ? lectureTeachingRepository.findCurrentSemester()
                : lectureTeachingRepository.findSemester(semNo);
        SemesterRow sem = semester.orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));

        // 교수는 담당 강의, 학생은 조교로 배정된 강의
        List<TeachingLectureRow> lectures = switch (user.role()) {
            case PROFESSOR -> lectureTeachingRepository.findByProfessor(sem.semNo(), user.usrNo());
            case STUDENT -> lectureTeachingRepository.findByAssistant(sem.semNo(), user.usrNo());
            case ADMIN -> throw new BusinessException(ErrorCode.FORBIDDEN); // 위에서 이미 막음
        };

        // 강의시간을 강의별로 묶는다
        Map<Integer, List<TeachingTimeSlot>> timeSlots =
                lectureTeachingRepository
                        .findTimeSlots(
                                lectures.stream().map(TeachingLectureRow::lecNo).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                TimeSlotRow::lecNo,
                                Collectors.mapping(LectureTeachingService::toTimeSlot, Collectors.toList())));

        List<TeachingLectureItem> items = lectures.stream()
                .map(row -> new TeachingLectureItem(
                        row.lecNo(),
                        row.crsCd(),
                        row.crsNm(),
                        row.sectNo(),
                        row.cap(),
                        row.enrlCnt(),
                        row.lecStat(),
                        timeSlots.getOrDefault(row.lecNo(), List.of())))
                .toList();
        return new TeachingLectureListResponse(sem.semNo(), sem.yr(), sem.semType(), items);
    }

    // 장소 표시: 온라인이면 "온라인", 강의실이 없으면 "미배정", 있으면 강의실명
    private static TeachingTimeSlot toTimeSlot(TimeSlotRow row) {
        String place;
        if (row.clsType() == ClsType.ONLINE) {
            place = PLACE_ONLINE;
        } else if (row.roomCd() == null) {
            place = PLACE_UNASSIGNED;
        } else {
            place = row.roomNm();
        }
        return new TeachingTimeSlot(row.dow(), row.startTm(), row.endTm(), row.clsType(), row.roomCd(), place);
    }
}
