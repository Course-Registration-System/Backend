package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 기본 시드(T0: 2026-2 성적 입력 중) 기준. 과목: 1010003 이산수학, 1010007 자료구조, 1010015 컴퓨터네트워크, 1030001 채플, 1030018 데이터리터러시. */
@Transactional
class ConfirmedGradeQueryTest extends IntegrationTest {

    @Autowired
    ConfirmedGradeQuery gradeQuery;

    @Test
    void 재수강한_과목은_가장_최근_학기의_성적이다() {
        // 2024-1 F → 2025-1 재수강 A-
        assertThat(gradeQuery.latestConfirmedGrade(Student.RETAKE, "1010003")).contains("A-");
    }

    @Test
    void 한_번_들은_과목은_그_성적이다() {
        assertThat(gradeQuery.latestConfirmedGrade(Student.RETAKE, "1010015")).contains("C+");
        assertThat(gradeQuery.latestConfirmedGrade(Student.PREREQ_NOT_MET, "1010007"))
                .contains("D+");
    }

    @Test
    void PF_과목은_P_또는_F다() {
        assertThat(gradeQuery.latestConfirmedGrade(Student.PASS_FAIL_ONLY, "1030001"))
                .contains("P");
    }

    @Test
    void 성적이_확정되지_않은_강의는_이력으로_보지_않는다() {
        // T0 에 2026-2 데이터리터러시는 성적 입력 중
        assertThat(gradeQuery.latestConfirmedGrade(Student.DEMO, "1030018")).isEmpty();
    }

    @Test
    void 듣지_않은_과목은_비어_있다() {
        assertThat(gradeQuery.latestConfirmedGrade(Student.DEMO, "1010015")).isEmpty();
    }
}
