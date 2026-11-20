package com.crs.backend.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.common.ClockConfig;
import com.crs.backend.common.SettableClock;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 학사일정으로 기간 판정을 확인합니다. 2026 겨울(10): 정정 12-21 10:00 ~ 12-22 17:00. 2027-1(11): 장바구니 01-18 ~ 01-22,
 * 수강신청 02-09(4학년) ~ 02-12(1학년), 정정 03-03 10:00 ~ 03-09 17:00.
 */
@Transactional
class AcademicSchedulePolicyTest extends IntegrationTest {

    @Autowired
    AcademicSchedulePolicy policy;

    @Autowired
    SettableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetClock() {
        at(SeedTime.T0);
    }

    private void at(String time) {
        clock.fixAt(LocalDateTime.parse(time).atZone(ClockConfig.ZONE).toInstant());
    }

    @Test
    void 일정이_가장_먼저_시작하는_끝나지_않은_학기가_신청_대상이다() {
        at(SeedTime.T0); // 겨울학기 정정 중, 2027-1 은 아직 장바구니 전
        assertThat(policy.registrationSemester()).contains(Semester.Y2026_WINTER);

        at("2026-12-22T17:00:00"); // 겨울학기 정정이 끝난 순간
        assertThat(policy.registrationSemester()).contains(Semester.Y2027_FIRST);
    }

    @Test
    void 장바구니_기간_전에도_다음_학기가_신청_대상이다() {
        at("2027-01-05T10:00:00");
        assertThat(policy.registrationSemester()).contains(Semester.Y2027_FIRST);
        assertThat(policy.cartOpen(Semester.Y2027_FIRST)).isFalse();
    }

    @Test
    void 정정이_모두_끝나면_신청_대상_학기가_없다() {
        at("2027-03-09T17:00:00");
        assertThat(policy.registrationSemester()).isEmpty();
    }

    @Test
    void 장바구니_기간은_시작_시각_포함_종료_시각_제외() {
        at("2027-01-18T10:00:00");
        assertThat(policy.cartOpen(Semester.Y2027_FIRST)).isTrue();
        at("2027-01-22T17:00:00");
        assertThat(policy.cartOpen(Semester.Y2027_FIRST)).isFalse();
    }

    @Test
    void 수강신청일에는_본인_학년만_신청할_수_있다() {
        at(SeedTime.T1); // 02-11 2학년 신청일
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 2)).isTrue();
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 3)).isFalse();
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 1)).isFalse();
    }

    @Test
    void 정정_기간과_대상학년이_없는_수강신청일에는_모든_학년이_신청할_수_있다() {
        at(SeedTime.T2); // 2027-1 정정
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 1)).isTrue();
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 4)).isTrue();

        at("2026-11-30T10:00:00"); // 겨울학기 수강신청(전 학년)
        assertThat(policy.enrollOpen(Semester.Y2026_WINTER, 3)).isTrue();
    }

    @Test
    void 장바구니_기간에는_신청할_수_없다() {
        at("2027-01-20T10:00:00");
        assertThat(policy.enrollOpen(Semester.Y2027_FIRST, 4)).isFalse();
    }

    @Test
    void 강의_변경_기한은_학사일정에서_읽는다() {
        assertThat(policy.cartStart(Semester.Y2027_FIRST)).contains(LocalDateTime.parse("2027-01-18T10:00:00"));
        assertThat(policy.enrollStart(Semester.Y2027_FIRST)).contains(LocalDateTime.parse("2027-02-09T10:00:00"));
        assertThat(policy.adjustStart(Semester.Y2027_FIRST)).contains(LocalDateTime.parse("2027-03-03T10:00:00"));
        assertThat(policy.adjustEnd(Semester.Y2027_FIRST)).contains(LocalDateTime.parse("2027-03-09T17:00:00"));
    }

    @Test
    void 일정이_없는_학기는_기한이_비어_있다() {
        jdbc.update("INSERT INTO semester (sem_no, yr, sem_type, cur_sem_yn) VALUES (99, 2028, 'FIRST', 'N')");
        assertThat(policy.cartStart(99)).isEmpty();
        assertThat(policy.adjustEnd(99)).isEmpty();
    }

    @Test
    void 최대학점은_산정_결과를_쓰고_없으면_학기와_학적으로_정한다() {
        assertThat(policy.maxCredit(Student.ON_LEAVE, Semester.Y2026_WINTER)).isEqualTo(3); // 산정 결과
        assertThat(policy.maxCredit(Student.DEMO, Semester.Y2027_FIRST)).isEqualTo(18); // T0 에는 2027-1 산정 전

        // 산정 결과가 없는 새 계절학기
        jdbc.update("INSERT INTO semester (sem_no, yr, sem_type, cur_sem_yn) VALUES (98, 2027, 'SUMMER', 'N')");
        assertThat(policy.maxCredit(Student.ON_LEAVE, 98)).isEqualTo(3); // 휴학
        assertThat(policy.maxCredit(Student.DEMO, 98)).isEqualTo(6); // 재학
    }
}
