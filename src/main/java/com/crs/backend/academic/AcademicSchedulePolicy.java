package com.crs.backend.academic;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SchedulePolicy} 의 구현. 학사일정은 관리자가 등록한 값(ACA-01)을 그대로 읽고, 지금 시각은 앱 시계로 판단합니다.
 *
 * <p>부르는 쪽 트랜잭션 밖에서 읽습니다(NOT_SUPPORTED). MySQL 은 트랜잭션의 첫 일반 SELECT 에서 읽기 스냅숏을 고정하므로, 이 판정을
 * 행 잠금보다 먼저 부르면 잠금을 얻은 뒤에도 다른 트랜잭션이 커밋한 행이 보이지 않습니다(수강신청 중복 검사가 뚫림). 판정만
 * 따로 읽어 부르는 쪽 스냅숏에 영향을 주지 않게 합니다.
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
public class AcademicSchedulePolicy implements SchedulePolicy {

    static final int REGULAR_DEFAULT_MAX_CREDIT = 18;
    static final int SEASONAL_ATTENDING_MAX_CREDIT = 6;
    static final int SEASONAL_ON_LEAVE_MAX_CREDIT = 3;

    private final AcademicScheduleRepository repository;
    private final Clock clock;

    public AcademicSchedulePolicy(AcademicScheduleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<Integer> registrationSemester() {
        return repository.findRegistrationSemester(now());
    }

    @Override
    public boolean cartOpen(int semNo) {
        return repository.isCartOpen(semNo, now());
    }

    @Override
    public boolean enrollOpen(int semNo, int yrLvl) {
        return repository.isEnrollOpen(semNo, yrLvl, now());
    }

    @Override
    public int maxCredit(String stdNo, int semNo) {
        return repository.findMaxCredit(stdNo, semNo).orElseGet(() -> defaultMaxCredit(stdNo, semNo));
    }

    // 산정 결과가 없는 학생(산정 후 등록·복학 등): 정규학기 18, 계절학기는 학적상태로 정한다(ACA-01)
    private int defaultMaxCredit(String stdNo, int semNo) {
        if (!repository.isSeasonal(semNo)) {
            return REGULAR_DEFAULT_MAX_CREDIT;
        }
        return repository.isOnLeave(stdNo) ? SEASONAL_ON_LEAVE_MAX_CREDIT : SEASONAL_ATTENDING_MAX_CREDIT;
    }

    @Override
    public Optional<LocalDateTime> cartStart(int semNo) {
        return repository.findCartStart(semNo);
    }

    @Override
    public Optional<LocalDateTime> enrollStart(int semNo) {
        return repository.findEnrollStart(semNo);
    }

    @Override
    public Optional<LocalDateTime> adjustStart(int semNo) {
        return repository.findAdjustStart(semNo);
    }

    @Override
    public Optional<LocalDateTime> adjustEnd(int semNo) {
        return repository.findAdjustEnd(semNo);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
