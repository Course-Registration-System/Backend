package com.crs.backend.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.FakeGradeQuery;
import com.crs.backend.support.FakeSchedulePolicy;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
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
 * 기간은 FakeSchedulePolicy, 성적은 FakeGradeQuery 로 정합니다. 기본은 2027-1 수강신청 기간이고 확정 성적은 없습니다.
 *
 * <p>쓰는 2027-1 강의: 198번 컴파일러(화·목 09:00, 3학점, 선수과목 없음), 218번 심리학의이해 2분반(화·목 09:00), 217번 같은
 * 과목 1분반, 209번 기업가정신(월·수 10:30, 3학점), 211번 채플(P/F), 193번 알고리즘(선수과목 자료구조 D-)
 */
@AutoConfigureMockMvc
@Transactional
class EnrollmentControllerTest extends IntegrationTest {

    private static final int COMPILER = Lecture.ONE_SEAT_LEFT;
    private static final int PSYCHOLOGY_1 = 217;
    private static final int PSYCHOLOGY_2 = Lecture.TWO_SECTIONS;
    private static final int ENTREPRENEURSHIP = 209;
    private static final int CHAPEL = 211;
    private static final int ALGORITHM = Lecture.PREREQ_TARGET;

    private static final String STUDENT = Student.GPA_EXACTLY_4;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeSchedulePolicy schedulePolicy;

    @Autowired
    FakeGradeQuery gradeQuery;

    @BeforeEach
    void setUp() {
        schedulePolicy.at(Semester.Y2027_FIRST, false);
        schedulePolicy.enrollOpen(true);
    }

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
        gradeQuery.reset();
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
    void 기간이_아니면_신청과_취소를_할_수_없다() throws Exception {
        schedulePolicy.enrollOpen(false);
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
    void 폐강_강의는_신청할_수_없다() throws Exception {
        enroll(STUDENT, Lecture.CLOSED).andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    @Test
    void 같은_과목은_분반이_달라도_하나만_신청한다() throws Exception {
        enroll(STUDENT, PSYCHOLOGY_1).andExpect(status().isCreated());
        enroll(STUDENT, PSYCHOLOGY_2).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED_COURSE"));
    }

    @Test
    void 재수강은_가장_최근_확정_성적이_C플러스_이하일_때만_한다() throws Exception {
        gradeQuery.put(STUDENT, "1010021", "B0");
        enroll(STUDENT, COMPILER).andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));

        gradeQuery.put(STUDENT, "1010021", "C+");
        enroll(STUDENT, COMPILER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retake").value(true));
    }

    @Test
    void PF_과목은_F일_때만_재수강한다() throws Exception {
        gradeQuery.put(STUDENT, "1030001", "P");
        enroll(STUDENT, CHAPEL).andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
    }

    @Test
    void 재수강은_10과목까지_한다() throws Exception {
        jdbc.update("UPDATE enrollment SET retake_yn = 'Y' WHERE std_no = ? AND enrl_stat = 'ENROLLED'", STUDENT);
        Integer retaken = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.crs_cd) FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                 WHERE e.std_no = ? AND e.retake_yn = 'Y' AND e.enrl_stat = 'ENROLLED' AND l.crs_cd <> '1010021'
                """, Integer.class, STUDENT);
        assertThat(retaken).isGreaterThanOrEqualTo(10);

        gradeQuery.put(STUDENT, "1010021", "C0");
        enroll(STUDENT, COMPILER).andExpect(jsonPath("$.code").value("RETAKE_LIMIT_EXCEEDED"));
    }

    @Test
    void 정원이_차면_신청할_수_없다() throws Exception {
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no = ?", COMPILER);
        enroll(STUDENT, COMPILER).andExpect(jsonPath("$.code").value("LECTURE_FULL"));
    }

    @Test
    void 이미_신청한_강의와_시간이_겹치면_신청할_수_없다() throws Exception {
        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        enroll(STUDENT, PSYCHOLOGY_2)
                .andExpect(jsonPath("$.code").value("TIME_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(COMPILER + "번 컴파일러 강의와 시간이 겹칩니다."));
    }

    @Test
    void 최대학점을_넘으면_신청할_수_없고_채플은_세지_않는다() throws Exception {
        schedulePolicy.maxCredit(3);
        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        enroll(STUDENT, ENTREPRENEURSHIP).andExpect(jsonPath("$.code").value("CREDIT_EXCEEDED"));
        enroll(STUDENT, CHAPEL).andExpect(status().isCreated());
    }

    @Test
    void 선수과목을_이수해야_신청할_수_있다() throws Exception {
        enroll(STUDENT, ALGORITHM)
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.detail").value("선수과목 자료구조을(를) 이수하지 않았습니다."));

        gradeQuery.put(STUDENT, "1010007", "F");
        enroll(STUDENT, ALGORITHM).andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_MET"));

        gradeQuery.put(STUDENT, "1010007", "D0");
        enroll(STUDENT, ALGORITHM).andExpect(status().isCreated());
    }

    @Test
    void 취소하면_인원이_줄고_다시_신청하면_기록을_되살린다() throws Exception {
        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        cancel(STUDENT, COMPILER).andExpect(status().isNoContent());
        assertThat(enrlCnt(COMPILER)).isZero();
        assertThat(enrlStat(COMPILER)).isEqualTo("CANCELLED");

        enroll(STUDENT, COMPILER).andExpect(status().isCreated());
        assertThat(enrlCnt(COMPILER)).isEqualTo(1);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE std_no = ? AND lec_no = ? AND cncl_dtm IS NULL",
                Integer.class,
                STUDENT,
                COMPILER);
        assertThat(rows).isEqualTo(1);
        assertThat(enrlStat(COMPILER)).isEqualTo("ENROLLED");
    }

    @Test
    void 신청하지_않은_강의는_취소할_수_없다() throws Exception {
        cancel(STUDENT, COMPILER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
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
