package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 성적 조회를 먼저 불러도 부르는 쪽 트랜잭션의 읽기 스냅숏이 고정되지 않는지 확인합니다(SchedulePolicySnapshotTest 와 같은 방식). */
class GradeQuerySnapshotTest extends IntegrationTest {

    private static final String MARK = "2030-02-01 00:00:00";

    @Autowired
    ConfirmedGradeQuery gradeQuery;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM academic_schedule WHERE start_dtm = ?", MARK);
    }

    @Test
    void 성적_조회를_먼저_불러도_뒤에_커밋된_행이_보인다() {
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Integer seen = caller.execute(status -> {
            gradeQuery.latestConfirmedGrade(Student.RETAKE, "1010003");
            other.executeWithoutResult(s -> jdbc.update(
                    "INSERT INTO academic_schedule (sem_no, schd_type, start_dtm, end_dtm) VALUES (?, 'GRADE', ?, ?)",
                    Semester.Y2027_FIRST,
                    MARK,
                    "2030-02-02 00:00:00"));
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic_schedule WHERE start_dtm = ?", Integer.class, MARK);
        });

        assertThat(seen).isEqualTo(1);
    }
}
