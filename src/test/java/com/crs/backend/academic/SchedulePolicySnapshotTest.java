package com.crs.backend.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Semester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판정을 먼저 불러도 부르는 쪽 트랜잭션의 읽기 스냅숏이 고정되지 않는지 확인합니다. 고정되면 행 잠금을 얻은 뒤에도 다른 트랜잭션이 커밋한 행이
 * 보이지 않아 수강신청의 중복·시간·학점 검사가 뚫립니다. 다른 트랜잭션의 커밋이 필요해 테스트 트랜잭션(롤백)을 쓰지 않고 직접 지웁니다.
 */
class SchedulePolicySnapshotTest extends IntegrationTest {

    private static final String MARK = "2030-01-01 00:00:00";

    @Autowired
    AcademicSchedulePolicy realPolicy;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM academic_schedule WHERE start_dtm = ?", MARK);
    }

    @Test
    void 판정을_먼저_불러도_뒤에_커밋된_행이_보인다() {
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Integer seen = caller.execute(status -> {
            realPolicy.registrationSemester(); // 수강신청이 학생 잠금 전에 부르는 자리
            other.executeWithoutResult(s -> jdbc.update(
                    "INSERT INTO academic_schedule (sem_no, schd_type, start_dtm, end_dtm) VALUES (?, 'GRADE', ?, ?)",
                    Semester.Y2027_FIRST,
                    MARK,
                    "2030-01-02 00:00:00"));
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic_schedule WHERE start_dtm = ?", Integer.class, MARK);
        });

        assertThat(seen).isEqualTo(1);
    }
}
