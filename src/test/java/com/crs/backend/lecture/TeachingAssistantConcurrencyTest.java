package com.crs.backend.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Student;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 조교 추가가 동시에 들어올 때 하나만 저장되고 나머지는 DUPLICATE_ASSISTANT 인지 봅니다. 두 요청이 모두 "배정 안 됨"을 확인한 뒤
 * 저장하면 늦은 쪽은 PK 중복(DuplicateKeyException)으로 실패하는데, 이것도 같은 오류로 바꾸는지 확인합니다.
 *
 * <p>요청마다 자기 트랜잭션이 필요해서 @Transactional 을 쓰지 않고, 넣은 배정은 root 계정으로 지웁니다.
 */
class TeachingAssistantConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 8;
    private static final String STUDENT = Student.PASS_FAIL_ONLY;

    @Autowired
    TeachingAssistantService teachingAssistantService;

    private final JdbcTemplate root = new JdbcTemplate(dataSourceAs("root"));

    @AfterEach
    void tearDown() {
        root.update(
                "DELETE FROM teaching_assistant_assignment WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                STUDENT);
    }

    @Test
    void 같은_조교를_동시에_추가하면_하나만_저장되고_나머지는_409() throws Exception {
        CurrentUser professor = new CurrentUser(Account.DEMO_PROFESSOR, Role.PROFESSOR);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<ErrorCode> results = new ArrayList<>();
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        teachingAssistantService.add(professor, Lecture.DEMO_GRADE, STUDENT);
                        return null;
                    } catch (BusinessException e) {
                        return e.errorCode();
                    }
                }));
            }
            start.countDown();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get()); // 처리되지 않은 예외(catch 가 없을 때의 DuplicateKeyException)는 여기서 실패한다
            }
        } finally {
            pool.shutdown();
        }

        assertThat(results).filteredOn(code -> code == null).hasSize(1);
        assertThat(results).filteredOn(code -> code != null).containsOnly(ErrorCode.DUPLICATE_ASSISTANT);
        assertThat(root.queryForObject(
                        "SELECT COUNT(*) FROM teaching_assistant_assignment WHERE lec_no = ? AND std_no = ?",
                        Integer.class,
                        Lecture.DEMO_GRADE,
                        STUDENT))
                .isEqualTo(1);
    }
}
