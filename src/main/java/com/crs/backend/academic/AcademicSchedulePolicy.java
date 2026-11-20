package com.crs.backend.academic;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link SchedulePolicy} 의 구현. 학사일정은 관리자가 등록한 값(ACA-01)을 그대로 읽고, 지금 시각은 앱 시계로 판단합니다. */
@Service
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
