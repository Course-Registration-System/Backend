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
 * 실제 학사일정으로 판정합니다. 기본 시각은 2027-1 정정 기간입니다.
 *
 * <p>쓰는 2027-1 강의: 컴파일러(화·목 09:00, 정원 4), 심리학의이해 2분반(화·목 09:00)
 */
@AutoConfigureMockMvc
@Transactional
class EnrollmentAdminControllerTest extends IntegrationTest {

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

    String admin;

    @BeforeEach
    void setUp() {
        TestClock.at(clock, TestClock.ADJUST);
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    @Test
    void 추가하면_현황에_관리자_처리로_보인다() throws Exception {
        add(COMPILER, STUDENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cap").value(4))
                .andExpect(jsonPath("$.enrlCnt").value(1))
                .andExpect(jsonPath("$.remaining").value(3))
                .andExpect(jsonPath("$.students[0].stdNo").value(STUDENT))
                .andExpect(jsonPath("$.students[0].procType").value("ADMIN"));
        mvc.perform(get(path(COMPILER)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[0].stdNo").value(STUDENT));
    }

    @Test
    void 정원이_찬_강의에_추가하면_정원을_1_늘린다() throws Exception {
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no = ?", COMPILER);
        add(COMPILER, STUDENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cap").value(5))
                .andExpect(jsonPath("$.enrlCnt").value(5))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void 기간_밖에는_추가와_취소를_할_수_없다() throws Exception {
        // 첫 수강신청일(2/9) 전
        TestClock.at(clock, TestClock.CART);
        add(COMPILER, STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));

        // 정정 종료(3/9 17:00) 뒤
        TestClock.at(clock, "2027-03-10T12:00:00");
        cancel(COMPILER, STUDENT).andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));
    }

    @Test
    void 정정_일정이_없는_학기는_마지막_수강신청일이_끝나면_추가와_취소를_할_수_없다() throws Exception {
        jdbc.update("DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'", Semester.Y2027_FIRST);

        // 마지막 수강신청일(1학년, 2/12 10:00 ~ 17:00) 중
        TestClock.at(clock, "2027-02-12T12:00:00");
        add(COMPILER, STUDENT).andExpect(status().isCreated());

        TestClock.at(clock, "2027-02-12T17:00:00");
        cancel(COMPILER, STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));
    }

    @Test
    void 정원_말고는_학생_신청과_같은_조건을_지킨다() throws Exception {
        add(COMPILER, Student.GRADUATE_ASSISTANT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADUATE_STUDENT_NOT_ALLOWED"));
        String stdNo = SeedLectures.studentNeverTook(jdbc, "1010021", "1030007");
        add(COMPILER, stdNo).andExpect(status().isCreated());
        add(SeedLectures.of(jdbc, "1030007", Semester.Y2027_FIRST, 2), stdNo)
                .andExpect(jsonPath("$.code").value("TIME_CONFLICT"));
        add(COMPILER, Student.ON_LEAVE).andExpect(jsonPath("$.code").value("STUDENT_STATUS_NOT_ALLOWED"));
        add(Lecture.CLOSED, STUDENT).andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    @Test
    void 취소하면_관리자_처리로_기록되고_인원이_준다() throws Exception {
        add(COMPILER, STUDENT).andExpect(status().isCreated());
        cancel(COMPILER, STUDENT).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(enrl_stat, '/', proc_type) FROM enrollment WHERE std_no = ? AND lec_no = ?",
                        String.class,
                        STUDENT,
                        COMPILER))
                .isEqualTo("CANCELLED/ADMIN");
        assertThat(jdbc.queryForObject("SELECT enrl_cnt FROM lecture WHERE lec_no = ?", Integer.class, COMPILER))
                .isZero();
        cancel(COMPILER, STUDENT).andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void 없는_학생과_강의는_404() throws Exception {
        add(COMPILER, "00000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get(path(999999)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    @Test
    void 관리자가_아니면_쓸_수_없다() throws Exception {
        mvc.perform(get(path(COMPILER))
                        .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, STUDENT, Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    private ResultActions add(int lecNo, String stdNo) throws Exception {
        return mvc.perform(post(path(lecNo))
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stdNo\": \"" + stdNo + "\"}"));
    }

    private ResultActions cancel(int lecNo, String stdNo) throws Exception {
        return mvc.perform(delete(path(lecNo) + "/" + stdNo).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private static String path(int lecNo) {
        return "/api/admin/lectures/" + lecNo + "/enrollments";
    }
}
