package com.crs.backend.support;

import com.crs.backend.academic.SchedulePolicy;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 테스트용 SchedulePolicy. academic 모듈의 실제 구현이 들어오면 지우고, 장바구니 테스트는 시계(Clock)와 시드 일정으로 바꿉니다.
 *
 * <p>테스트가 값을 바꿨으면 끝날 때 reset() 으로 되돌립니다.
 */
@Component
public class FakeSchedulePolicy implements SchedulePolicy {

    private Integer registrationSemester;
    private boolean cartOpen;
    private int maxCredit;

    public FakeSchedulePolicy() {
        reset();
    }

    public void reset() {
        registrationSemester = null;
        cartOpen = false;
        maxCredit = 18;
    }

    /** 신청 대상 학기와 장바구니 기간 여부를 정합니다. */
    public void at(Integer semNo, boolean cartOpen) {
        this.registrationSemester = semNo;
        this.cartOpen = cartOpen;
    }

    @Override
    public Optional<Integer> registrationSemester() {
        return Optional.ofNullable(registrationSemester);
    }

    @Override
    public boolean cartOpen(int semNo) {
        return cartOpen && Integer.valueOf(semNo).equals(registrationSemester);
    }

    @Override
    public int maxCredit(String stdNo, int semNo) {
        return maxCredit;
    }
}
