package com.crs.backend.support;

import com.crs.backend.academic.AcademicScheduleRepository;
import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.academic.SemesterRepository;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트용 SchedulePolicy. academic 모듈의 실제 구현이 들어오면 지우고, 장바구니·수강신청 테스트는 시계(Clock)와 시드 일정으로 바꿉니다.
 *
 * <p>테스트가 값을 바꿨으면 끝날 때 reset() 으로 되돌립니다. 실제 구현(AcademicSchedulePolicy)도 빈이라 @Primary 로 이쪽이 주입됩니다.
 */
@Primary
@Component
public class FakeSchedulePolicy implements SchedulePolicy {

    private Integer registrationSemester;
    private boolean cartOpen;
    private boolean enrollOpen;
    private int maxCredit;

    private final AcademicScheduleRepository academicScheduleRepository;
    private final SemesterRepository semesterRepository;

    public FakeSchedulePolicy(
            AcademicScheduleRepository academicScheduleRepository, SemesterRepository semesterRepository) {
        this.academicScheduleRepository = academicScheduleRepository;
        this.semesterRepository = semesterRepository;
        reset();
    }

    public void reset() {
        registrationSemester = null;
        cartOpen = false;
        enrollOpen = false;
        maxCredit = 18;
    }

    /** 신청 대상 학기와 장바구니 기간 여부를 정합니다. */
    public void at(Integer semNo, boolean cartOpen) {
        this.registrationSemester = semNo;
        this.cartOpen = cartOpen;
    }

    /** 수강신청·정정 기간 여부를 정합니다(학년 구분 없이). */
    public void enrollOpen(boolean enrollOpen) {
        this.enrollOpen = enrollOpen;
    }

    public void maxCredit(int maxCredit) {
        this.maxCredit = maxCredit;
    }

    @Override
    public Optional<Integer> registrationSemester() {
        return Optional.ofNullable(registrationSemester);
    }

    /** 실제 구현과 같은 규칙. 신청 대상 학기만 at() 으로 정한 값을 쓰고, 학기 존재와 현재 학기는 DB 에서 읽습니다. */
    @Override
    public int semesterOrDefault(Integer semNo) {
        if (semNo != null) {
            if (semesterRepository.findById(semNo).isEmpty()) {
                throw new BusinessException(ErrorCode.SEMESTER_NOT_FOUND);
            }
            return semNo;
        }
        return registrationSemester()
                .or(academicScheduleRepository::findCurrentSemester)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    @Override
    public boolean cartOpen(int semNo) {
        return cartOpen && Integer.valueOf(semNo).equals(registrationSemester);
    }

    @Override
    public boolean enrollOpen(int semNo, int yrLvl) {
        return enrollOpen && Integer.valueOf(semNo).equals(registrationSemester);
    }

    @Override
    public int maxCredit(String stdNo, int semNo) {
        return maxCredit;
    }

    @Override
    public Optional<LocalDateTime> cartStart(int semNo) {
        return Optional.empty();
    }

    @Override
    public Optional<LocalDateTime> enrollStart(int semNo) {
        return Optional.empty();
    }

    @Override
    public Optional<LocalDateTime> adjustStart(int semNo) {
        return Optional.empty();
    }

    @Override
    public Optional<LocalDateTime> adjustEnd(int semNo) {
        return Optional.empty();
    }
}
