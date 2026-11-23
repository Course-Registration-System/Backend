package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 등급 기준표(GRD-01)와, 평점 SQL 식(GradePoint)이 같은 평점을 내는지. */
class LetterGradeTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 총점_구간의_경계에서_등급이_바뀐다() {
        assertThat(LetterGrade.fromScore(100)).isEqualTo(LetterGrade.A_PLUS);
        assertThat(LetterGrade.fromScore(97)).isEqualTo(LetterGrade.A_PLUS);
        assertThat(LetterGrade.fromScore(96)).isEqualTo(LetterGrade.A_ZERO);
        assertThat(LetterGrade.fromScore(90)).isEqualTo(LetterGrade.A_MINUS);
        assertThat(LetterGrade.fromScore(89)).isEqualTo(LetterGrade.B_PLUS);
        assertThat(LetterGrade.fromScore(80)).isEqualTo(LetterGrade.B_MINUS);
        assertThat(LetterGrade.fromScore(79)).isEqualTo(LetterGrade.C_PLUS);
        assertThat(LetterGrade.fromScore(60)).isEqualTo(LetterGrade.D_MINUS);
        assertThat(LetterGrade.fromScore(59)).isEqualTo(LetterGrade.F);
        assertThat(LetterGrade.fromScore(0)).isEqualTo(LetterGrade.F);
    }

    @Test
    void 재수강은_A마이너스가_상한이고_그_아래는_그대로() {
        assertThat(LetterGrade.fromScore(100, true)).isEqualTo(LetterGrade.A_MINUS);
        assertThat(LetterGrade.fromScore(94, true)).isEqualTo(LetterGrade.A_MINUS);
        assertThat(LetterGrade.fromScore(88, true)).isEqualTo(LetterGrade.B_PLUS);
    }

    @Test
    void A등급과_B등급_구분() {
        assertThat(LetterGrade.A_MINUS.isA()).isTrue();
        assertThat(LetterGrade.B_PLUS.isA()).isFalse();
        assertThat(LetterGrade.B_PLUS.isB()).isTrue();
        assertThat(LetterGrade.B_MINUS.isB()).isTrue();
        assertThat(LetterGrade.C_PLUS.isB()).isFalse();
        assertThat(LetterGrade.A_ZERO.isB()).isFalse();
    }

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
