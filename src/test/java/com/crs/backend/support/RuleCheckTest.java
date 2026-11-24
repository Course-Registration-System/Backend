package com.crs.backend.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V3·V6 업무 규칙 CHECK 가 서비스를 거치지 않는 SQL 도 막는지 확인합니다. MySQL CHECK 위반(3819)은 Spring 이 따로 분류하지 않아 제약
 * 이름으로 봅니다. 허용하는 쪽은 시드 적재와 기존 API 테스트가 지나갑니다.
 */
@Transactional
class RuleCheckTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 신청인원이_정원을_넘으면_DB가_거부한다() {
        assertRejected(
                "ck_lecture_enrl_cnt_cap",
                "UPDATE lecture SET enrl_cnt = cap + 1 WHERE lec_no = ?",
                Lecture.ONE_SEAT_LEFT);
    }

    @Test
    void 폐강_강의에_신청인원이_있으면_DB가_거부한다() {
        assertRejected("ck_lecture_closed_no_enrl", "UPDATE lecture SET enrl_cnt = 1 WHERE lec_no = ?", Lecture.CLOSED);
    }

    @Test
    void 확정자_없이_확정하면_DB가_거부한다() {
        assertRejected(
                "ck_lecture_confirm_emp",
                "UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_dtm = '2030-01-01 10:00:00', cnfm_emp_no = NULL"
                        + " WHERE lec_no = ?",
                Lecture.ONE_SEAT_LEFT);
    }

    @Test
    void 채플이_0_5학점_PF가_아니면_DB가_거부한다() {
        String chapel = jdbc.queryForObject(
                "SELECT crs_cd FROM course WHERE cmpl_type = 'CHAPEL' ORDER BY crs_cd LIMIT 1", String.class);
        assertRejected("ck_course_chapel", "UPDATE course SET crdt = 1 WHERE crs_cd = ?", chapel);
    }

    @Test
    void 사용자번호가_역할별_자리수와_다르면_DB가_거부한다() {
        String insert = "INSERT INTO user_account (usr_no, pwd, usr_nm, role) VALUES (?, 'x', '새사용자', ?)";
        assertRejected("ck_user_account_usr_no", insert, "9002", "ADMIN");
        assertRejected("ck_user_account_usr_no", insert, "202799999", "STUDENT");
        assertRejected("ck_user_account_usr_no", insert, "1099999", "PROFESSOR");
    }

    @Test
    void 대학원생의_신청_장바구니_최대학점은_DB가_거부한다() {
        assertRejected(
                "ck_enrollment_undergraduate",
                "INSERT INTO enrollment (std_no, lec_no, enrl_stat, enrl_dtm) VALUES (?, ?, 'ENROLLED', '2027-02-15 10:00:00')",
                Student.GRADUATE_ASSISTANT,
                Lecture.ONE_SEAT_LEFT);
        assertRejected(
                "ck_course_cart_undergraduate",
                "INSERT INTO course_cart (std_no, lec_no) VALUES (?, ?)",
                Student.GRADUATE_ASSISTANT,
                Lecture.ONE_SEAT_LEFT);
        assertRejected(
                "ck_semester_maximum_credit_undergraduate",
                "INSERT INTO semester_maximum_credit (std_no, sem_no, max_crdt) VALUES (?, ?, 18)",
                Student.GRADUATE_ASSISTANT,
                Semester.Y2027_FIRST);
    }

    private void assertRejected(String constraint, String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(constraint);
    }
}
