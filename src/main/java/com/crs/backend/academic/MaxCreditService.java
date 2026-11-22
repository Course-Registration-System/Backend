package com.crs.backend.academic;

import com.crs.backend.academic.dto.MaxCreditItem;
import com.crs.backend.academic.dto.MaxCreditResult;
import com.crs.backend.academic.dto.SemesterResponse;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최대학점 산정(ACA-01 ⑤). 정규학기면 같은 트랜잭션에서 진급·졸업을 먼저 처리합니다(D27). 그래야 졸업생이 최대학점을 받는 순서 실수가
 * 없습니다.
 *
 * <p>복학 처리처럼 학적이 바뀌는 시기가 여러 번이라 몇 번이든 다시 실행할 수 있고, 다시 실행해도 결과가 같습니다. 학년은 이력에서 다시
 * 계산하고, 졸업은 되돌리지 않으며, 최대학점은 그 학기 행을 지우고 새로 넣습니다.
 *
 * <p>다시 실행할 수 있는 건 그 학기 일정(장바구니)이 시작되기 전까지입니다. 신청 도중에 바꾸면 학년별 수강신청일과 이미 쓰고 있는 최대학점이
 * 바뀌기 때문입니다.
 */
@Service
public class MaxCreditService {

    private static final int SHOWN_LECTURES = 10;

    private final MaxCreditRepository maxCreditRepository;
    private final SemesterRepository semesterRepository;
    private final Clock clock;

    public MaxCreditService(
            MaxCreditRepository maxCreditRepository, SemesterRepository semesterRepository, Clock clock) {
        this.maxCreditRepository = maxCreditRepository;
        this.semesterRepository = semesterRepository;
        this.clock = clock;
    }

    @Transactional
    public MaxCreditResult calculate(int semNo) {
        SemesterResponse semester = get(semNo);
        if (semesterRepository.anyScheduleStarted(semNo, LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.MAX_CREDIT_CLOSED);
        }
        int targetOrd = order(semester);
        int graduated = 0;
        maxCreditRepository.deleteBySemester(semNo);

        if (semester.regular()) {
            checkPreviousConfirmed(targetOrd);
            maxCreditRepository.recalculateYearLevels(targetOrd);
            graduated = maxCreditRepository.graduate(targetOrd);
            maxCreditRepository.insertRegular(semNo, targetOrd);
        } else {
            maxCreditRepository.insertSeasonal(semNo);
        }

        return new MaxCreditResult(semNo, semester.regular(), graduated, maxCreditRepository.countByMaxCredit(semNo));
    }

    public List<MaxCreditItem> list(int semNo) {
        get(semNo);
        return maxCreditRepository.findAll(semNo);
    }

    /** 정규학기 산정은 직전 정규학기 성적이 모두 확정된 뒤에만 합니다(선행 조건 GRD-02). 확정 안 된 강의를 알려 주고 거부합니다. */
    private void checkPreviousConfirmed(int targetOrd) {
        Integer previous = maxCreditRepository.findPreviousRegular(targetOrd);
        if (previous == null) {
            return;
        }
        List<String> unconfirmed = maxCreditRepository.findUnconfirmedLectures(previous);
        if (unconfirmed.isEmpty()) {
            return;
        }
        String shown = String.join(", ", unconfirmed.subList(0, Math.min(SHOWN_LECTURES, unconfirmed.size())));
        String more = unconfirmed.size() > SHOWN_LECTURES ? " 외 " + (unconfirmed.size() - SHOWN_LECTURES) + "개" : "";
        throw new BusinessException(ErrorCode.GRADES_NOT_CONFIRMED, "직전 정규학기에 성적이 확정되지 않은 강의가 있습니다: " + shown + more);
    }

    private static int order(SemesterResponse semester) {
        return semester.yr() * 10 + semester.semType().ordinal() + 1;
    }

    private SemesterResponse get(int semNo) {
        return semesterRepository
                .findById(semNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));
    }
}
