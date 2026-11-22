package com.crs.backend.support;

import com.crs.backend.common.ClockConfig;
import com.crs.backend.common.SettableClock;
import java.time.LocalDateTime;

/**
 * 테스트 중에 시계를 옮깁니다. 시계는 테스트끼리 같이 쓰는 빈이라, 옮긴 테스트는 끝날 때 reset() 으로 T0 에 되돌립니다.
 *
 * <p>2027-1 시드 일정: 장바구니 1/18~22, 수강신청 2/9(4학년)~2/12(1학년), 정정 3/3~3/9.
 */
public final class TestClock {

    /** 2027-1 장바구니 기간 */
    public static final String CART = "2027-01-20T12:00:00";

    /** 2027-1 3학년 수강신청일 */
    public static final String ENROLL_3RD_YEAR = "2027-02-10T12:00:00";

    /** 2027-1 정정 기간(모든 학년 신청 가능) */
    public static final String ADJUST = "2027-03-05T12:00:00";

    private TestClock() {}

    public static void at(SettableClock clock, String localDateTime) {
        clock.fixAt(LocalDateTime.parse(localDateTime).atZone(ClockConfig.ZONE).toInstant());
    }

    public static void reset(SettableClock clock) {
        at(clock, SeedTime.T0);
    }
}
