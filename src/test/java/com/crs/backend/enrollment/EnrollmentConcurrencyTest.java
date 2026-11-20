package com.crs.backend.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.FakeSchedulePolicy;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 여러 요청이 동시에 들어올 때 잠금이 규칙을 지키는지 봅니다. 요청마다 자기 트랜잭션이 필요해서 @Transactional 을 쓰지 않고, 바꾼 데이터는
 * root 계정으로 되돌립니다.
 */
class EnrollmentConcurrencyTest extends IntegrationTest {

    // 209번 기업가정신(월·수 10:30), 198번 컴파일러와 218번 심리학의이해 2분반(둘 다 화·목 09:00)
    private static final int ENTREPRENEURSHIP = 209;
    private static final List<Integer> LECTURES =
            List.of(ENTREPRENEURSHIP, Lecture.ONE_SEAT_LEFT, Lecture.TWO_SECTIONS);

    @Autowired
    EnrollmentService enrollmentService;

    @Autowired
    FakeSchedulePolicy schedulePolicy;

    private final JdbcTemplate root = new JdbcTemplate(dataSourceAs("root"));

    private List<Integer> originalCaps;

    @BeforeEach
    void setUp() {
        schedulePolicy.at(Semester.Y2027_FIRST, false);
        schedulePolicy.enrollOpen(true);
        originalCaps = LECTURES.stream()
                .map(lecNo -> root.queryForObject("SELECT cap FROM crs.lecture WHERE lec_no = ?", Integer.class, lecNo))
                .toList();
        assertThat(enrolledRows()).isZero();
    }

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
        String in = "(" + ENTREPRENEURSHIP + ", " + Lecture.ONE_SEAT_LEFT + ", " + Lecture.TWO_SECTIONS + ")";
        root.update("DELETE FROM crs.enrollment WHERE lec_no IN " + in);
        for (int i = 0; i < LECTURES.size(); i++) {
            root.update(
                    "UPDATE crs.lecture SET cap = ?, enrl_cnt = 0 WHERE lec_no = ?",
                    originalCaps.get(i),
                    LECTURES.get(i));
        }
    }

    @Test
    void 여러_학생이_동시에_신청해도_정원을_넘지_않는다() throws Exception {
        root.update("UPDATE crs.lecture SET cap = 3 WHERE lec_no = ?", ENTREPRENEURSHIP);
        List<String> students = root.queryForList(
                "SELECT std_no FROM crs.student WHERE std_stat = 'ATTENDING' ORDER BY std_no LIMIT 10", String.class);
        assertThat(students).hasSize(10);

        List<ErrorCode> results = runAtOnce(students.stream()
                .map(stdNo -> (Callable<ErrorCode>) () -> enroll(stdNo, ENTREPRENEURSHIP))
                .toList());

        assertThat(results).filteredOn(code -> code == null).hasSize(3);
        assertThat(results).filteredOn(code -> code != null).containsOnly(ErrorCode.LECTURE_FULL);
        assertThat(root.queryForObject(
                        "SELECT enrl_cnt FROM crs.lecture WHERE lec_no = ?", Integer.class, ENTREPRENEURSHIP))
                .isEqualTo(3);
        assertThat(enrolledRows()).isEqualTo(3);
    }

    @Test
    void 한_학생이_시간이_겹치는_두_강의를_동시에_신청하면_하나만_된다() throws Exception {
        String stdNo = Student.GPA_EXACTLY_4;
        List<ErrorCode> results = runAtOnce(
                List.of(() -> enroll(stdNo, Lecture.ONE_SEAT_LEFT), () -> enroll(stdNo, Lecture.TWO_SECTIONS)));

        assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.TIME_CONFLICT);
        assertThat(enrolledRows()).isEqualTo(1);
    }

    /** 성공하면 null, 막히면 그 오류 코드. */
    private ErrorCode enroll(String stdNo, int lecNo) {
        try {
            enrollmentService.enroll(new CurrentUser(stdNo, Role.STUDENT), lecNo);
            return null;
        } catch (BusinessException e) {
            return e.errorCode();
        }
    }

    private List<ErrorCode> runAtOnce(List<Callable<ErrorCode>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (Callable<ErrorCode> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private int enrolledRows() {
        return root.queryForObject(
                "SELECT COUNT(*) FROM crs.enrollment WHERE enrl_stat = 'ENROLLED' AND lec_no IN (?, ?, ?)",
                Integer.class,
                ENTREPRENEURSHIP,
                Lecture.ONE_SEAT_LEFT,
                Lecture.TWO_SECTIONS);
    }
}
