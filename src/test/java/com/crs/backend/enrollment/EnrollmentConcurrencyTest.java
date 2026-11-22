package com.crs.backend.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.SettableClock;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.lecture.LectureAdminService;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedLectures;
import com.crs.backend.support.TestClock;
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
 * 여러 요청이 동시에 들어올 때 잠금이 규칙을 지키는지 봅니다. 실제 학사일정·성적 판정을 쓰므로, 판정이 트랜잭션 밖에서 읽어도 검사가
 * 엇갈리지 않는지까지 봅니다. 요청마다 자기 트랜잭션이 필요해서 @Transactional 을 쓰지 않고, 바꾼 데이터는 root 계정으로 되돌립니다.
 *
 * <p>시각은 2027-1 정정 기간. 쓰는 강의: 기업가정신(월·수 10:30), 컴파일러와 심리학의이해 2분반(둘 다 화·목 09:00)
 */
class EnrollmentConcurrencyTest extends IntegrationTest {

    @Autowired
    EnrollmentService enrollmentService;

    @Autowired
    LectureAdminService lectureAdminService;

    @Autowired
    SettableClock clock;

    private final JdbcTemplate root = new JdbcTemplate(dataSourceAs("root"));

    private int entrepreneurship;
    private List<Integer> lectures;
    private List<Integer> originalCaps;

    @BeforeEach
    void setUp() {
        TestClock.at(clock, TestClock.ADJUST);
        entrepreneurship = SeedLectures.of(root, "1020020", Semester.Y2027_FIRST, 1);
        lectures = List.of(entrepreneurship, Lecture.ONE_SEAT_LEFT, Lecture.TWO_SECTIONS);
        originalCaps = lectures.stream()
                .map(lecNo -> root.queryForObject("SELECT cap FROM lecture WHERE lec_no = ?", Integer.class, lecNo))
                .toList();
        assertThat(enrolledRows()).isZero();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
        for (int i = 0; i < lectures.size(); i++) {
            root.update("DELETE FROM enrollment WHERE lec_no = ?", lectures.get(i));
            root.update(
                    "UPDATE lecture SET cap = ?, enrl_cnt = 0, lec_stat = 'OPEN' WHERE lec_no = ?",
                    originalCaps.get(i),
                    lectures.get(i));
        }
    }

    @Test
    void 여러_학생이_동시에_신청해도_정원을_넘지_않는다() throws Exception {
        root.update("UPDATE lecture SET cap = 3 WHERE lec_no = ?", entrepreneurship);
        List<String> students = attendingStudents();

        List<ErrorCode> results = runAtOnce(students.stream()
                .map(stdNo -> (Callable<ErrorCode>) () -> enroll(stdNo, entrepreneurship))
                .toList());

        assertThat(results).filteredOn(code -> code == null).hasSize(3);
        assertThat(results).filteredOn(code -> code != null).containsOnly(ErrorCode.LECTURE_FULL);
        assertThat(enrlCnt(entrepreneurship)).isEqualTo(3);
        assertThat(enrolledRows()).isEqualTo(3);
    }

    @Test
    void 한_학생이_시간이_겹치는_두_강의를_동시에_신청하면_하나만_된다() throws Exception {
        String stdNo = SeedLectures.studentNeverTook(root, "1010021", "1030007");
        List<ErrorCode> results = runAtOnce(
                List.of(() -> enroll(stdNo, Lecture.ONE_SEAT_LEFT), () -> enroll(stdNo, Lecture.TWO_SECTIONS)));

        assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.TIME_CONFLICT);
        assertThat(enrolledRows()).isEqualTo(1);
    }

    @Test
    void 폐강과_신청이_동시에_들어와도_폐강_강의에_신청이_남지_않는다() throws Exception {
        List<Callable<ErrorCode>> tasks = new ArrayList<>(attendingStudents().stream()
                .map(stdNo -> (Callable<ErrorCode>) () -> enroll(stdNo, entrepreneurship))
                .toList());
        tasks.add(() -> {
            lectureAdminService.close(entrepreneurship);
            return null;
        });

        List<ErrorCode> results = runAtOnce(tasks);

        assertThat(results).filteredOn(code -> code != null).containsOnly(ErrorCode.LECTURE_CLOSED);
        assertThat(root.queryForObject("SELECT lec_stat FROM lecture WHERE lec_no = ?", String.class, entrepreneurship))
                .isEqualTo("CLOSED");
        assertThat(enrlCnt(entrepreneurship)).isZero();
        assertThat(enrolledRows()).isZero();
    }

    /** 1020020 과목을 들은 적 없는 재학생. 들은 적 있으면 재수강 규칙에 막힌다. */
    private List<String> attendingStudents() {
        List<String> students = root.queryForList("""
                SELECT s.std_no FROM student s
                 WHERE s.std_stat = 'ATTENDING'
                   AND NOT EXISTS (SELECT 1 FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                                    WHERE e.std_no = s.std_no AND l.crs_cd = '1020020')
                 ORDER BY s.std_no LIMIT 10
                """, String.class);
        assertThat(students).hasSize(10);
        return students;
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

    private int enrlCnt(int lecNo) {
        return root.queryForObject("SELECT enrl_cnt FROM lecture WHERE lec_no = ?", Integer.class, lecNo);
    }

    private int enrolledRows() {
        return root.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE enrl_stat = 'ENROLLED' AND lec_no IN (?, ?, ?)",
                Integer.class,
                lectures.get(0),
                lectures.get(1),
                lectures.get(2));
    }
}
