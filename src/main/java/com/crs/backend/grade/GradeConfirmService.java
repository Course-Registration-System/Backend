package com.crs.backend.grade;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.grade.GradeConfirmRepository.LockedLecture;
import com.crs.backend.grade.GradeConfirmRepository.SemesterRow;
import com.crs.backend.grade.dto.ConfirmedLecture;
import com.crs.backend.grade.dto.GradeConfirmRequest;
import com.crs.backend.grade.dto.GradeConfirmResponse;
import com.crs.backend.grade.dto.GradeStatusLecture;
import com.crs.backend.grade.dto.GradeStatusResponse;
import com.crs.backend.lecture.LecStat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 성적 확정(GRD-02). 관리자가 학기별 성적상태를 보고, 성적 입력 기간이 끝난 뒤 제출된 강의를 확정합니다. 폐강 강의는 대상에서 빠집니다. */
@Service
public class GradeConfirmService {

    private final GradeConfirmRepository gradeConfirmRepository;
    private final SchedulePolicy schedulePolicy;
    private final Clock clock;

    public GradeConfirmService(
            GradeConfirmRepository gradeConfirmRepository, SchedulePolicy schedulePolicy, Clock clock) {
        this.gradeConfirmRepository = gradeConfirmRepository;
        this.schedulePolicy = schedulePolicy;
        this.clock = clock;
    }

    /**
     * 학기별 성적상태. semNo 가 null 이면 현재 학기입니다(신청 대상 학기가 아니라 성적을 마감할 학기). 없는 학기이거나 현재 학기가 지정되지
     * 않았으면 404 SEMESTER_NOT_FOUND 입니다. grdStat 를 주면 그 상태의 강의만 목록에 담습니다(counts 는 학기 전체).
     */
    @Transactional(readOnly = true)
    public GradeStatusResponse status(Integer semNo, GrdStat grdStat) {
        SemesterRow sem = (semNo == null
                        ? gradeConfirmRepository.findCurrentSemester()
                        : gradeConfirmRepository.findSemester(semNo))
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));

        List<GradeStatusLecture> lectures = gradeConfirmRepository.findLectures(sem.semNo());
        Map<GrdStat, Integer> counts = new EnumMap<>(GrdStat.class);
        for (GrdStat stat : GrdStat.values()) {
            counts.put(stat, 0);
        }
        lectures.forEach(l -> counts.merge(l.grdStat(), 1, Integer::sum));

        Optional<LocalDateTime> gradeEnd = schedulePolicy.gradeEnd(sem.semNo());
        List<GradeStatusLecture> shown = grdStat == null
                ? lectures
                : lectures.stream().filter(l -> l.grdStat() == grdStat).toList();
        return new GradeStatusResponse(
                sem.semNo(), sem.yr(), sem.semType(), gradeEnd.orElse(null), periodOver(gradeEnd), counts, shown);
    }

    /** 확정. 강의마다 검사하고, 하나라도 안 되면 아무것도 확정하지 않습니다. 확정자는 로그인한 관리자입니다. */
    @Transactional
    public GradeConfirmResponse confirm(CurrentUser user, GradeConfirmRequest request) {
        List<Integer> lecNos = request.lecNos();
        if (lecNos.stream().distinct().count() != lecNos.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 강의번호를 두 번 넣었습니다.");
        }

        // 강의번호 순으로 잠그고 읽는다(트랜잭션의 첫 조회)
        Map<Integer, LockedLecture> locked = gradeConfirmRepository.lockLectures(lecNos).stream()
                .collect(Collectors.toMap(LockedLecture::lecNo, Function.identity()));
        List<Integer> ordered = lecNos.stream().sorted().toList();

        // 모두 검사한 뒤 확정한다
        Map<Integer, Optional<LocalDateTime>> gradeEnds = new HashMap<>();
        for (int lecNo : ordered) {
            LockedLecture lecture = locked.get(lecNo);
            if (lecture == null) {
                throw fail(ErrorCode.LECTURE_NOT_FOUND, lecNo);
            }
            if (lecture.lecStat() == LecStat.CLOSED) {
                throw fail(ErrorCode.LECTURE_CLOSED, lecNo);
            }
            Optional<LocalDateTime> gradeEnd = gradeEnds.computeIfAbsent(lecture.semNo(), schedulePolicy::gradeEnd);
            if (!periodOver(gradeEnd)) {
                throw fail(ErrorCode.GRADE_PERIOD_NOT_OVER, lecNo);
            }
            if (lecture.grdStat() == GrdStat.CONFIRMED) {
                throw fail(ErrorCode.GRADE_CONFIRMED, lecNo);
            }
            if (lecture.grdStat() != GrdStat.SUBMITTED) {
                throw fail(ErrorCode.GRADE_NOT_SUBMITTED, lecNo);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<ConfirmedLecture> confirmed = ordered.stream()
                .map(lecNo -> {
                    gradeConfirmRepository.confirm(lecNo, user.usrNo(), now);
                    return new ConfirmedLecture(lecNo, locked.get(lecNo).crsNm(), GrdStat.CONFIRMED, now);
                })
                .toList();
        return new GradeConfirmResponse(confirmed);
    }

    /** 성적 입력 기간이 끝났는지. 끝 시각부터 확정할 수 있고, 성적입력 일정이 없으면 끝나지 않은 것으로 봅니다. */
    private boolean periodOver(Optional<LocalDateTime> gradeEnd) {
        LocalDateTime now = LocalDateTime.now(clock);
        return gradeEnd.map(end -> !now.isBefore(end)).orElse(false);
    }

    private static BusinessException fail(ErrorCode code, int lecNo) {
        return new BusinessException(code, code.message() + " (강의번호 " + lecNo + ")");
    }
}
