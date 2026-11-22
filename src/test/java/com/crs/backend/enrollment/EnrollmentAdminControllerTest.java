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
import com.crs.backend.support.FakeSchedulePolicy;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedLectures;
import com.crs.backend.support.SeedTime;
import java.time.LocalDateTime;
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
 * 시계는 T0(2026-12-21 10:00)이고, 첫 수강신청일과 정정 종료는 FakeSchedulePolicy 로 정합니다. 기본은 T0 가 그 사이입니다.
 *
 * <p>쓰는 2027-1 강의: 컴파일러(화·목 09:00, 정원 4), 심리학의이해 2분반(화·목 09:00)
 */
@AutoConfigureMockMvc
@Transactional
class EnrollmentAdminControllerTest extends IntegrationTest {

    private static final int COMPILER = Lecture.ONE_SEAT_LEFT;
    private static final String STUDENT = Student.GPA_EXACTLY_4;
    private static final LocalDateTime NOW = LocalDateTime.parse(SeedTime.T0);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeSchedulePolicy schedulePolicy;

    String admin;

    @BeforeEach
    void setUp() {
        schedulePolicy.enrollPeriod(NOW.minusDays(1), NOW.plusDays(1));
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
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
        schedulePolicy.enrollPeriod(NOW.plusDays(1), NOW.plusDays(2));
        add(COMPILER, STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));

        schedulePolicy.enrollPeriod(NOW.minusDays(2), NOW);
        cancel(COMPILER, STUDENT).andExpect(jsonPath("$.code").value("ENROLL_CLOSED"));
    }

    @Test
    void 정원_말고는_학생_신청과_같은_조건을_지킨다() throws Exception {
        add(COMPILER, STUDENT).andExpect(status().isCreated());
        add(SeedLectures.of(jdbc, "1030007", Semester.Y2027_FIRST, 2), STUDENT)
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
