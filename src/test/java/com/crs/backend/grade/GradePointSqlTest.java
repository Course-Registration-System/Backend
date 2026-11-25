package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 등급표(LetterGrade)의 평점과 평점 SQL 식(GradePoint)이 같은지. SQL 을 실행해야 해서 통합 테스트로 둡니다. */
class GradePointSqlTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 평점은_GradePoint_SQL_식과_같다() {
        for (LetterGrade grade : LetterGrade.values()) {
            Double fromSql = jdbc.queryForObject(
                    "SELECT " + GradePoint.sql("g.ltr_grd") + " FROM (SELECT ? AS ltr_grd) g",
                    Double.class,
                    grade.code());
            assertThat(fromSql).as(grade.code()).isEqualTo(grade.point());
        }
    }
}
