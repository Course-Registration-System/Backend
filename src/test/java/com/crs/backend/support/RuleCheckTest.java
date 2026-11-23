package com.crs.backend.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crs.backend.support.SeedFixtures.Lecture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V3 업무 규칙 CHECK 가 서비스를 거치지 않는 SQL 도 막는지 확인합니다. MySQL CHECK 위반(3819)은 Spring 이 따로 분류하지 않아 제약
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

    private void assertRejected(String constraint, String sql, Object arg) {
        assertThatThrownBy(() -> jdbc.update(sql, arg))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(constraint);
    }
}
