package com.crs.backend.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.SettableClock;
import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedLectures;
import com.crs.backend.support.TestClock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 학사일정·성적으로 판정합니다. 기본 시각은 2027-1 정정 기간(모든 학년 신청 가능)입니다.
 *
 * <p>학사일정과 성적은 이 테스트의 트랜잭션 밖에서 읽으므로, 성적은 시드 사례를 쓰고 최대학점은 커밋해서 넣고 지웁니다.
 *
 * <p>쓰는 2027-1 강의: 컴파일러(화·목 09:00, 3학점, 선수과목 없음), 심리학의이해 1·2분반(2분반은 화·목 09:00), 기업가정신(월·수
 * 10:30, 3학점), 채플(P/F, 0.5학점), 이산수학·컴퓨터네트워크(재수강 사례), 알고리즘(선수과목 자료구조 D-)·데이터베이스(선수과목 자료구조 C0)
 */
@AutoConfigureMockMvc
@Transactional
class EnrollmentControllerTest extends IntegrationTest {

    private static final int COMPILER = Lecture.ONE_SEAT_LEFT;
    private static final String STUDENT = Student.GPA_EXACTLY_4;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    @BeforeEach
    void setUp() {
        TestClock.at(clock, TestClock.ADJUST);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    @Test
    void 신청하면_신청인원이_늘고_내역에_보인다() throws Exception {
        enroll(STUDENT, COMPILER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecNo").value(COMPILER))
                .andExpect(jsonPath("$.retake").value(false));
        assertThat(enrlCnt(COMPILER)).isEqualTo(1);
        mvc.perform(get("/api/enrollments").header(HttpHeaders.AUTHORIZATION, student(STUDENT)))
                .andExpect(jsonPath("$[0].lecNo").value(COMPILER))
                .andExpect(jsonPath("$[0].timeSlots").isNotEmpty());
    }

    @Test
    void 본인_학년_수강신청일이_아니면_신청과_취소를_할_수_없다() throws Exception {
        // 4학년 수강신청일. STUDENT 는 3학년
        TestClock.at(clock, "2027-02-09T12:00:00");
        enroll(STUDENT, COMPILER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));
        cancel(STUDENT, COMPILER).andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));
    }

    @Test
    void 휴학생은_정규학기에_신청할_수_없다() throws Exception {
        enroll(Student.ON_LEAVE, COMPILER).andExpect(jsonPath("$.code").value("STUDENT_STATUS_NOT_ALLOWED"));
    }

    @Test
    void 대학원생은_신청할_수_없다() throws Exception {
        enroll(Student.TEACHING_ASSISTANT, COMPILER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADUATE_STUDENT_NOT_ALLOWED"));
        // 다른 학년의 신청일에도 기간 안내가 아니라 대학원생 불가를 알린다
        TestClock.at(clock, TestClock.ENROLL_3RD_YEAR);
        enroll(Student.TEACHING_ASSISTANT, COMPILER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADUATE_STUDENT_NOT_ALLOWED"));
    }

    @Test
    void 폐강_강의는_신청할_수_없다() throws Exception {
        enroll(STUDENT, Lecture.CLOSED).andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    @Test
    void 같은_과목은_분반이_달라도_하나만_신청한다() throws Exception {
        String stdNo = SeedLectures.studentNeverTook(jdbc, "1030007");
        enroll(stdNo, lecture("1030007", 1)).andExpect(status().isCreated());
        enroll(stdNo, lecture("1030007", 2)).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED_COURSE"));
    }

    @Test
    void 재수강은_가장_최근_확정_성적이_C플러스_이하일_때만_한다() throws Exception {
        // RETAKE 학생: 이산수학 F → 재수강 A-(가장 최근), 컴퓨터네트워크 C+
        enroll(Student.RETAKE, lecture("1010003", 1))
                .andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
        enroll(Student.RETAKE, lecture("1010015", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retake").value(true));
    }

    @Test
    void PF_과목은_P를_받았으면_재수강할_수_없다() throws Exception {
        // PASS_FAIL_ONLY 학생은 2026-2 사회봉사 P. T0 에는 아직 성적 확정 전이라, 이 테스트 동안만 확정해 두고 되돌린다
        JdbcTemplate root = new JdbcTemplate(dataSourceAs("root"));
        int serviceLastTerm = SeedLectures.of(root, "1030002", Semester.Y2026_SECOND, 1);
        String original =
                root.queryForObject("SELECT grd_stat FROM lecture WHERE lec_no = ?", String.class, serviceLastTerm);
        root.update(
                "UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = ?, cnfm_dtm = '2026-12-24 10:00:00'"
                        + " WHERE lec_no = ?",
                Account.ADMIN,
                serviceLastTerm);
        try {
            enroll(Student.PASS_FAIL_ONLY, lecture("1030002", 1))
                    .andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
        } finally {
            root.update(
                    "UPDATE lecture SET grd_stat = ?, cnfm_emp_no = NULL, cnfm_dtm = NULL WHERE lec_no = ?",
                    original,
                    serviceLastTerm);
        }
    }

    @Test
    void 채플은_P를_받았어도_학기마다_신청하고_재수강이_아니다() throws Exception {
        // PASS_FAIL_ONLY 학생은 지난 학기 채플 P
        int chapel = lecture("1030001", 1);
        enroll(Student.PASS_FAIL_ONLY, chapel)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retake").value(false));
        enroll(Student.PASS_FAIL_ONLY, chapel).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED_COURSE"));
    }

    @Test
    void 재수강은_10과목까지_한다() throws Exception {
        // 컴퓨터네트워크를 뺀 나머지 과목을 모두 재수강한 것으로 만든다
        jdbc.update("""
                UPDATE enrollment e JOIN lecture l ON l.lec_no = e.lec_no SET e.retake_yn = 'Y'
                 WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.crs_cd <> '1010015'
                """, Student.RETAKE);
        Integer retaken = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.crs_cd) FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                 WHERE e.std_no = ? AND e.retake_yn = 'Y' AND e.enrl_stat = 'ENROLLED' AND l.crs_cd <> '1010015'
                """, Integer.class, Student.RETAKE);
        assertThat(retaken).isGreaterThanOrEqualTo(10);

        enroll(Student.RETAKE, lecture("1010015", 1))
                .andExpect(jsonPath("$.code").value("RETAKE_LIMIT_EXCEEDED"));
    }

    @Test
    void 정원이_차면_신청할_수_없다() throws Exception {
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no = ?", COMPILER);
        enroll(STUDENT, COMPILER).andExpect(jsonPath("$.code").value("LECTURE_FULL"));
    }

    @Test
    void 이미_신청한_강의와_시간이_겹치면_신청할_수_없다() throws Exception {
        String stdNo = SeedLectures.studentNeverTook(jdbc, "1010021", "1030007");
        enroll(stdNo, COMPILER).andExpect(status().isCreated());
        enroll(stdNo, lecture("1030007", 2))
                .andExpect(jsonPath("$.code").value("TIME_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(COMPILER + "번 컴파일러 강의와 시간이 겹칩니다."));
    }

    @Test
    void 최대학점을_넘으면_신청할_수_없고_채플은_세지_않는다() throws Exception {
        JdbcTemplate root = new JdbcTemplate(dataSourceAs("root"));
        root.update(
                "INSERT INTO semester_maximum_credit (std_no, sem_no, max_crdt) VALUES (?, ?, 3)",
                STUDENT,
                Semester.Y2027_FIRST);
        try {
            enroll(STUDENT, COMPILER).andExpect(status().isCreated());
            enroll(STUDENT, lecture("1020020", 1)).andExpect(jsonPath("$.code").value("CREDIT_EXCEEDED"));
            enroll(STUDENT, lecture("1030001", 1)).andExpect(status().isCreated());
        } finally {
            root.update(
                    "DELETE FROM semester_maximum_credit WHERE std_no = ? AND sem_no = ?",
                    STUDENT,
                    Semester.Y2027_FIRST);
        }
    }

    @Test
    void 선수과목을_이수해야_신청할_수_있다() throws Exception {
        // PREREQ_NOT_MET 학생: 자료구조 D+
        enroll(Student.PREREQ_NOT_MET, lecture("1010014", 1))
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.detail").value("선수과목 자료구조을(를) 이수하지 않았습니다."));
        enroll(Student.PREREQ_NOT_MET, lecture("1010013", 1)).andExpect(status().isCreated());
    }

    @Test
    void 취소하면_인원이_줄고_다시_신청하면_기록을_되살린다() throws Exception {
        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        cancel(STUDENT, COMPILER).andExpect(status().isNoContent());
        assertThat(enrlCnt(COMPILER)).isZero();
        assertThat(enrlStat(COMPILER)).isEqualTo("CANCELLED");

        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        assertThat(enrlCnt(COMPILER)).isEqualTo(1);
        List<String> rows = jdbc.queryForList(
                "SELECT enrl_stat FROM enrollment WHERE std_no = ? AND lec_no = ? AND cncl_dtm IS NULL",
                String.class,
                STUDENT,
                COMPILER);
        assertThat(rows).containsExactly("ENROLLED");
    }

    @Test
    void 신청하지_않은_강의는_취소할_수_없다() throws Exception {
        cancel(STUDENT, COMPILER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    private int lecture(String crsCd, int sectNo) {
        return SeedLectures.of(jdbc, crsCd, Semester.Y2027_FIRST, sectNo);
    }

    private int enrlCnt(int lecNo) {
        return jdbc.queryForObject("SELECT enrl_cnt FROM lecture WHERE lec_no = ?", Integer.class, lecNo);
    }

    private String enrlStat(int lecNo) {
        return jdbc.queryForObject(
                "SELECT enrl_stat FROM enrollment WHERE std_no = ? AND lec_no = ?", String.class, STUDENT, lecNo);
    }

    private ResultActions enroll(String stdNo, int lecNo) throws Exception {
        return mvc.perform(post("/api/enrollments")
                .header(HttpHeaders.AUTHORIZATION, student(stdNo))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lecNo\": " + lecNo + "}"));
    }

    private ResultActions cancel(String stdNo, int lecNo) throws Exception {
        return mvc.perform(delete("/api/enrollments/" + lecNo).header(HttpHeaders.AUTHORIZATION, student(stdNo)));
    }

    private String student(String stdNo) {
        return Bearer.of(jwtProvider, stdNo, Role.STUDENT);
    }
}
