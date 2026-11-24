package com.crs.backend.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedTime;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진급·졸업과 최대학점의 경계 사례. 학생마다 이수 내역을 직접 만들고 2027-1 산정을 실행합니다. 2026-2 와 2026 겨울 성적이 확정돼야
 * 정규학기 산정이 되므로 s1 상태에서 시작합니다. 모든 데이터는 테스트가 끝나면 롤백됩니다.
 */
@Transactional
class PromotionTest extends IntegrationTest {

    // 시드의 정규학기: 2023-1(1) ~ 2026-1(7), 2026-2(9). 여름 2026(8), 겨울 2026(10)
    private static final int[] REGULAR = {1, 2, 3, 4, 5, 6, 7, 9};
    private static final int SUMMER = 8;
    private static final int WINTER = Semester.Y2026_WINTER;
    private static final AtomicInteger COURSE_SEQ = new AtomicInteger();

    @Autowired
    MaxCreditService maxCreditService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void setUp() {
        SeedTime.apply(dataSource, SeedTime.S1);
    }

    @Test
    void 대학원생은_학년을_다시_계산하지_않고_최대학점을_받지_않는다() {
        // 대학원생(학번 10자리)은 조교로만 쓴다. 학년은 관리자가 USR-02 에서 정한 값 그대로 둔다
        jdbc.update("UPDATE student SET yr_lvl = 2 WHERE std_no = ?", Student.TEACHING_ASSISTANT);

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(jdbc.queryForObject(
                        "SELECT yr_lvl FROM student WHERE std_no = ?", Integer.class, Student.TEACHING_ASSISTANT))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM semester_maximum_credit WHERE std_no = ?",
                        Integer.class,
                        Student.TEACHING_ASSISTANT))
                .isZero();
    }

    @Test
    void 정규_8학기_130학점_채플_6학기면_졸업하고_최대학점을_받지_않는다() {
        History s = graduateCandidate("29990001", 6);

        int graduated = maxCreditService.calculate(Semester.Y2027_FIRST).graduated();

        assertThat(graduated).isEqualTo(1);
        assertThat(s.status()).isEqualTo("GRADUATED");
        assertThat(s.maxCredit()).isNull();
    }

    @Test
    void 채플이_5학기면_졸업하지_않고_4학년_재학으로_남는다() {
        // 졸업 요건을 못 채우면 추가학기를 다닌다: 재학을 유지하고 다음 정규학기 최대학점을 받는다
        History s = graduateCandidate("29990001", 5);

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("ATTENDING");
        assertThat(s.yearLevel()).isEqualTo(4);
        assertThat(s.maxCredit()).isNotNull();
    }

    @Test
    void 겨울학기_학점도_졸업_이수_학점에_넣는다() {
        // 정규학기 128학점 + 겨울 2학점 = 130학점, 채플 P 6학기
        History s = regularSemesters("29990001", 8, "A0");
        chapels(s, 6);
        s.take(WINTER, "2.0", "GEN_ELEC", "GRADE", "A0");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("GRADUATED");
    }

    @Test
    void 계절학기에_받은_채플_P도_센다() {
        History s = graduateCandidate("29990001", 5);
        s.take(WINTER, "0.5", "CHAPEL", "PASS_FAIL", "P");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("GRADUATED");
    }

    @Test
    void 겨울학기_성적이_확정되지_않았으면_산정을_막고_졸업시키지_않는다() {
        // 정규학기 채플 P 5학기 + 겨울 채플 P. 겨울 성적이 확정되면 졸업할 학생이다
        History s = graduateCandidate("29990001", 5);
        s.takeUnconfirmed(WINTER, "0.5", "CHAPEL", "PASS_FAIL", "P");

        assertThatThrownBy(() -> maxCreditService.calculate(Semester.Y2027_FIRST))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GRADES_NOT_CONFIRMED));

        assertThat(s.status()).isEqualTo("ATTENDING");
        assertThat(s.maxCredit()).isNull();
    }

    @Test
    void 겨울학기_성적은_최대학점_평점에_넣지_않는다() {
        // 2026-2 B+ 3학점 + 겨울 A+ 6학점: 겨울을 넣으면 4.17 이라 21 이지만 2026-2 만 봐서 18
        History low = new History("29990001");
        low.take(9, "3.0", "MAJOR_ELEC", "GRADE", "B+");
        low.take(WINTER, "3.0", "MAJOR_ELEC", "GRADE", "A+");
        low.take(WINTER, "3.0", "MAJOR_ELEC", "GRADE", "A+");
        // 2026-2 A0 3학점 + 겨울 F 3학점: 겨울을 넣으면 2.15 라 18 이지만 2026-2 만 봐서 21
        History high = new History("29990002");
        high.take(9, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        high.take(WINTER, "3.0", "MAJOR_ELEC", "GRADE", "F");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(low.maxCredit()).isEqualTo(18);
        assertThat(high.maxCredit()).isEqualTo(21);
    }

    @Test
    void 채플_학점은_졸업_이수_학점에_넣지_않는다() {
        // 등급제 128학점 + 1.5학점 = 129.5, 채플 6학기 3학점을 더하면 132.5지만 졸업하지 않는다
        History s = regularSemesters("29990001", 8, "A0");
        s.take(9, "1.5", "GEN_ELEC", "GRADE", "A0");
        chapels(s, 6);

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("ATTENDING");
    }

    @Test
    void 재수강한_과목은_가장_최근_성적만_센다() {
        // 128학점 + 2학점 과목을 C+ 뒤에 F로 재수강 → 최근 성적 F라 128학점
        History s = regularSemesters("29990001", 8, "A0");
        String retaken = s.take(1, "2.0", "MAJOR_ELEC", "GRADE", "C+");
        s.retake(retaken, 9, "F");
        chapels(s, 6);

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("ATTENDING");
    }

    @Test
    void 정규학기가_7개면_학점과_채플을_채워도_졸업하지_않는다() {
        // 7학기 × 16학점 + 4학점 5과목 = 132학점
        History s = regularSemesters("29990001", 7, "A0");
        for (int i = 0; i < 5; i++) {
            s.take(7, "4.0", "MAJOR_ELEC", "GRADE", "A0");
        }
        chapels(s, 6);

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.status()).isEqualTo("ATTENDING");
        assertThat(s.yearLevel()).isEqualTo(4);
    }

    @Test
    void 학년은_다닌_정규학기_수로_정하고_휴학생도_다시_계산한다() {
        History threeTerms = regularSemesters("29990001", 3, "B0");
        History onLeave = regularSemesters("29990002", 2, "B0");
        jdbc.update("UPDATE student SET std_stat = 'ON_LEAVE' WHERE std_no = ?", "29990002");
        History freshman = new History("29990003");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(threeTerms.yearLevel()).isEqualTo(2);
        assertThat(onLeave.yearLevel()).isEqualTo(2);
        assertThat(onLeave.maxCredit()).isNull();
        assertThat(freshman.yearLevel()).isEqualTo(1);
        assertThat(freshman.maxCredit()).isEqualTo(18);
    }

    @Test
    void 직전_정규학기_평점이_정확히_4점이면_21학점_399면_18학점() {
        History exactly4 = new History("29990001");
        exactly4.take(9, "3.0", "MAJOR_ELEC", "GRADE", "A-");
        exactly4.take(9, "3.0", "MAJOR_ELEC", "GRADE", "A-");
        // 3.5학점 A- 7과목 + 0.5학점 B+ = (98 + 1.75) / 25 = 3.99
        History under4 = new History("29990002");
        for (int i = 0; i < 7; i++) {
            under4.take(9, "3.5", "MAJOR_ELEC", "GRADE", "A-");
        }
        under4.take(9, "0.5", "MAJOR_ELEC", "GRADE", "B+");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(exactly4.maxCredit()).isEqualTo(21);
        assertThat(under4.maxCredit()).isEqualTo(18);
    }

    @Test
    void 마지막으로_다닌_정규학기로_산정하고_계절학기와_PF_과목은_보지_않는다() {
        // 2026-1에 4.3, 휴학으로 2026-2는 건너뜀, 여름학기 F → 2026-1 평점으로 21
        History returning = new History("29990001");
        returning.take(7, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        returning.take(SUMMER, "3.0", "MAJOR_ELEC", "GRADE", "F");
        // 3학기째 학생의 마지막 정규학기가 P/F 과목뿐이면 평점이 없어 18 (그 전 학기가 4.3이어도)
        History passFailOnly = new History("29990002");
        passFailOnly.take(6, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        passFailOnly.take(7, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        passFailOnly.take(9, "0.5", "CHAPEL", "PASS_FAIL", "P");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(returning.maxCredit()).isEqualTo(21);
        assertThat(passFailOnly.maxCredit()).isEqualTo(18);
    }

    @Test
    void 이학년_일학기는_일학년_두_학기를_합친_학점_가중_평균으로_정한다() {
        // 2026-1 A+ 3학점, 2026-2 B+ 1학점: 마지막 학기만 보면 3.5지만 1학년 평균은 (13.5 + 3.5) / 4 = 4.25 → 21
        History risen = new History("29990001");
        risen.take(7, "3.0", "MAJOR_ELEC", "GRADE", "A+");
        risen.take(9, "1.0", "MAJOR_ELEC", "GRADE", "B+");
        // 2026-1 C+ 3학점, 2026-2 A0 3학점: 마지막 학기만 보면 4.3이지만 1학년 평균은 3.4 → 18
        History fallen = new History("29990002");
        fallen.take(7, "3.0", "MAJOR_ELEC", "GRADE", "C+");
        fallen.take(9, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        // 3학기째는 다시 마지막 학기만 본다: 2025-2 F, 2026-1 C+, 2026-2 A0 → 4.3 → 21
        History third = new History("29990003");
        third.take(6, "3.0", "MAJOR_ELEC", "GRADE", "F");
        third.take(7, "3.0", "MAJOR_ELEC", "GRADE", "C+");
        third.take(9, "3.0", "MAJOR_ELEC", "GRADE", "A0");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(risen.maxCredit()).isEqualTo(21);
        assertThat(fallen.maxCredit()).isEqualTo(18);
        assertThat(third.maxCredit()).isEqualTo(21);
    }

    @Test
    void 다시_산정하면_이번_대상에서_빠진_학생의_지난_값을_지운다() {
        History s = regularSemesters("29990001", 2, "A0");
        maxCreditService.calculate(Semester.Y2027_FIRST);
        assertThat(s.maxCredit()).isEqualTo(21);

        jdbc.update("UPDATE student SET std_stat = 'ON_LEAVE' WHERE std_no = ?", "29990001");
        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.maxCredit()).isNull();
    }

    @Test
    void 성적_미확정으로_막히면_그_학기의_기존_최대학점을_지우지_않는다() {
        History s = regularSemesters("29990001", 2, "A0");
        maxCreditService.calculate(Semester.Y2027_FIRST);
        assertThat(s.maxCredit()).isEqualTo(21);

        // 서비스 예외로 테스트 트랜잭션이 롤백 전용이 되지만 롤백은 테스트가 끝날 때라 같은 트랜잭션에서 남은 값을 읽을 수 있다
        s.takeUnconfirmed(WINTER, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        assertThatThrownBy(() -> maxCreditService.calculate(Semester.Y2027_FIRST))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GRADES_NOT_CONFIRMED));

        assertThat(s.maxCredit()).isEqualTo(21);
    }

    @Test
    void 직전_정규학기보다_앞_학기의_미확정_강의는_산정을_막지_않는다() {
        // 2027-1 은 직전 정규학기 2026-2 부터 본다. 2026-1 과 2026 여름의 미확정 강의는 보지 않는다
        History s = new History("29990001");
        s.takeUnconfirmed(7, "3.0", "MAJOR_ELEC", "GRADE", "A0");
        s.takeUnconfirmed(SUMMER, "3.0", "MAJOR_ELEC", "GRADE", "A0");

        maxCreditService.calculate(Semester.Y2027_FIRST);

        assertThat(s.maxCredit()).isEqualTo(18);
    }

    /** 정규 8학기, 학기마다 4학점 A0 4과목(128학점) + 2학점 1과목 = 130학점, 채플 P 학기 n개. */
    private History graduateCandidate(String stdNo, int chapelTerms) {
        History s = regularSemesters(stdNo, 8, "A0");
        s.take(9, "2.0", "GEN_ELEC", "GRADE", "A0");
        chapels(s, chapelTerms);
        return s;
    }

    /** 앞에서부터 정규학기 n개에 4학점 과목 4개씩. */
    private History regularSemesters(String stdNo, int terms, String grade) {
        History s = new History(stdNo);
        for (int t = 0; t < terms; t++) {
            for (int i = 0; i < 4; i++) {
                s.take(REGULAR[t], "4.0", "MAJOR_ELEC", "GRADE", grade);
            }
        }
        return s;
    }

    private void chapels(History s, int terms) {
        for (int t = 0; t < terms; t++) {
            s.take(REGULAR[t], "0.5", "CHAPEL", "PASS_FAIL", "P");
        }
    }

    /** 한 학생의 이수 내역을 만듭니다. 과목·강의는 새로 만들고 강의는 성적 확정 상태로 둡니다. */
    private class History {

        final String stdNo;

        History(String stdNo) {
            this.stdNo = stdNo;
            jdbc.update(
                    "INSERT INTO user_account (usr_no, pwd, usr_nm, role) VALUES (?, 'x', '테스트', 'STUDENT')", stdNo);
            jdbc.update("INSERT INTO student (std_no, dept_no, yr_lvl) VALUES (?, 101, 1)", stdNo);
        }

        /** 새 과목을 만들어 그 학기에 이수합니다. 과목코드를 돌려줍니다. */
        String take(int semNo, String crdt, String cmplType, String evalType, String grade) {
            String crsCd = newCourse(crdt, cmplType, evalType);
            enroll(crsCd, semNo, grade);
            return crsCd;
        }

        /** 새 과목을 그 학기에 듣고 성적은 제출만 된(확정 전) 상태로 둡니다. */
        void takeUnconfirmed(int semNo, String crdt, String cmplType, String evalType, String grade) {
            String crsCd = newCourse(crdt, cmplType, evalType);
            jdbc.update(
                    "INSERT INTO lecture (crs_cd, sem_no, prof_no, sect_no, cap, grd_stat) VALUES (?, ?, ?, 1, 50, 'SUBMITTED')",
                    crsCd,
                    semNo,
                    Account.DEMO_PROFESSOR);
            addEnrollment(crsCd, semNo, grade);
        }

        private String newCourse(String crdt, String cmplType, String evalType) {
            String crsCd = "Z" + "%05d".formatted(COURSE_SEQ.incrementAndGet());
            jdbc.update(
                    "INSERT INTO course (crs_cd, dept_no, crs_nm, crdt, cmpl_type, eval_type) VALUES (?, 101, ?, ?, ?, ?)",
                    crsCd,
                    "테스트" + crsCd,
                    new BigDecimal(crdt),
                    cmplType,
                    evalType);
            return crsCd;
        }

        /** 같은 과목을 다른 학기에 다시 듣습니다. */
        void retake(String crsCd, int semNo, String grade) {
            enroll(crsCd, semNo, grade);
        }

        private void enroll(String crsCd, int semNo, String grade) {
            jdbc.update(
                    "INSERT INTO lecture (crs_cd, sem_no, prof_no, sect_no, cap, grd_stat, cnfm_emp_no, cnfm_dtm)"
                            + " VALUES (?, ?, ?, 1, 50, 'CONFIRMED', ?, '2026-12-28 10:00:00')",
                    crsCd,
                    semNo,
                    Account.DEMO_PROFESSOR,
                    Account.ADMIN);
            addEnrollment(crsCd, semNo, grade);
        }

        private void addEnrollment(String crsCd, int semNo, String grade) {
            Integer lecNo = jdbc.queryForObject(
                    "SELECT lec_no FROM lecture WHERE crs_cd = ? AND sem_no = ?", Integer.class, crsCd, semNo);
            jdbc.update("INSERT INTO enrollment (std_no, lec_no) VALUES (?, ?)", stdNo, lecNo);
            jdbc.update("INSERT INTO grade (std_no, lec_no, ltr_grd) VALUES (?, ?, ?)", stdNo, lecNo, grade);
        }

        String status() {
            return row().get("std_stat").toString();
        }

        int yearLevel() {
            return ((Number) row().get("yr_lvl")).intValue();
        }

        Integer maxCredit() {
            return jdbc
                    .queryForList(
                            "SELECT max_crdt FROM semester_maximum_credit WHERE std_no = ? AND sem_no = ?",
                            Integer.class,
                            stdNo,
                            Semester.Y2027_FIRST)
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        private Map<String, Object> row() {
            return jdbc.queryForMap("SELECT yr_lvl, std_stat FROM student WHERE std_no = ?", stdNo);
        }
    }
}
