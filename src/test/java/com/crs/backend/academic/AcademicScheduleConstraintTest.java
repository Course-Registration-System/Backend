package com.crs.backend.academic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Semester;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 서비스를 거치지 않는 SQL 도 DB 제약이 막는지 확인합니다. MySQL CHECK 위반(3819)은 Spring 이 따로 분류하지 않아 제약 이름으로 봅니다. */
@Transactional
class AcademicScheduleConstraintTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 수강신청이_아닌_일정에_대상학년을_넣으면_DB가_거부한다() {
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO academic_schedule (sem_no, schd_type, tgt_yr_lvl, start_dtm, end_dtm)
                        VALUES (?, 'CART', 1, '2030-01-01 10:00:00', '2030-01-02 10:00:00')
                        """, Semester.Y2027_FIRST))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_academic_schedule_tgt_yr_lvl_enroll");
    }
}
