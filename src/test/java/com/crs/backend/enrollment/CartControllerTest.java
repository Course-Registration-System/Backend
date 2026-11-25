package com.crs.backend.enrollment;

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

/** 실제 학사일정으로 판정합니다. 기본 시각은 2027-1 장바구니 기간입니다. */
@AutoConfigureMockMvc
@Transactional
class CartControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    // 2027-1 채플. 0.5학점이지만 담은 학점에 넣지 않는다
    int chapel;

    // 2026 겨울학기 강의
    int winter;

    @BeforeEach
    void setUp() {
        TestClock.at(clock, TestClock.CART);
        chapel = SeedLectures.of(jdbc, "1030001", Semester.Y2027_FIRST, 1);
        winter = SeedLectures.of(jdbc, "1030006", Semester.Y2026_WINTER, 1);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    @Test
    void 담으면_담은_인원과_학점이_보인다() throws Exception {
        double credit = jdbc.queryForObject(
                "SELECT c.crdt FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd WHERE l.lec_no = ?",
                Double.class,
                Lecture.ONE_SEAT_LEFT);

        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT).andExpect(status().isCreated());
        add(Student.GPA_JUST_UNDER_4, Lecture.ONE_SEAT_LEFT).andExpect(status().isCreated());
        // 2027-1 최대학점 산정 전이라 정규학기 기본값. 이 학생은 T0 에 3학년이라 19
        add(Student.GPA_EXACTLY_4, chapel)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2027_FIRST))
                .andExpect(jsonPath("$.items[0].lecNo").value(Lecture.ONE_SEAT_LEFT))
                .andExpect(jsonPath("$.items[0].cartCnt").value(2))
                .andExpect(jsonPath("$.items[0].timeSlots").isNotEmpty())
                .andExpect(jsonPath("$.items[1].lecNo").value(chapel))
                .andExpect(jsonPath("$.cartCredits").value(credit))
                .andExpect(jsonPath("$.maxCredit").value(19));
    }

    @Test
    void 정원이_찬_강의도_담을_수_있다() throws Exception {
        add(Student.GPA_EXACTLY_4, Lecture.FULL).andExpect(status().isCreated());
    }

    @Test
    void 대학원생은_담을_수_없다() throws Exception {
        // 대학원생(학번 10자리)은 조교로만 쓴다(USR-01)
        add(Student.GRADUATE_ASSISTANT, Lecture.ONE_SEAT_LEFT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADUATE_STUDENT_NOT_ALLOWED"));
        // 최대학점 산정 대상이 아니므로 담을 수 있는 학점도 0으로 보인다
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, student(Student.GRADUATE_ASSISTANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCredit").value(0));
    }

    @Test
    void 장바구니_기간이_아니면_담거나_뺄_수_없다() throws Exception {
        TestClock.at(clock, TestClock.ENROLL_3RD_YEAR);
        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CLOSED"));
        remove(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT)
                .andExpect(jsonPath("$.code").value("CART_CLOSED"));
        // 수강신청·정정 기간에도 목록은 본다
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, student(Student.GPA_EXACTLY_4)))
                .andExpect(status().isOk());
    }

    @Test
    void 신청_대상_학기가_없으면_목록도_볼_수_없다() throws Exception {
        // 2027-1 정정이 끝난 뒤. 시드에 다음 학기가 없다
        TestClock.at(clock, "2027-04-01T12:00:00");
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, student(Student.GPA_EXACTLY_4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_REGISTRATION_SEMESTER"));
    }

    @Test
    void 같은_강의는_두_번_담을_수_없다() throws Exception {
        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT).andExpect(status().isCreated());
        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CART"));
    }

    @Test
    void 폐강_강의와_다른_학기_강의는_담을_수_없다() throws Exception {
        add(Student.GPA_EXACTLY_4, Lecture.CLOSED).andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        add(Student.GPA_EXACTLY_4, Lecture.NOT_ENTERED)
                .andExpect(jsonPath("$.code").value("OTHER_SEMESTER_LECTURE"));
        add(Student.GPA_EXACTLY_4, 999999).andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    @Test
    void 정규학기에는_휴학생과_졸업생이_담을_수_없다() throws Exception {
        add(Student.ON_LEAVE, Lecture.ONE_SEAT_LEFT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_STATUS_NOT_ALLOWED"));
        jdbc.update("UPDATE student SET std_stat = 'GRADUATED' WHERE std_no = ?", Student.GPA_EXACTLY_4);
        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT)
                .andExpect(jsonPath("$.code").value("STUDENT_STATUS_NOT_ALLOWED"));
    }

    @Test
    void 계절학기에는_휴학생도_담을_수_있다() throws Exception {
        // 2026 겨울학기 장바구니 기간(11/23~25)
        TestClock.at(clock, "2026-11-24T12:00:00");
        add(Student.ON_LEAVE, winter).andExpect(status().isCreated());
    }

    @Test
    void 뺀_강의는_목록에서_사라진다() throws Exception {
        add(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT).andExpect(status().isCreated());
        remove(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, student(Student.GPA_EXACTLY_4)))
                .andExpect(jsonPath("$.items").isEmpty());
        remove(Student.GPA_EXACTLY_4, Lecture.ONE_SEAT_LEFT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_NOT_FOUND"));
    }

    @Test
    void 학생이_아니면_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    private ResultActions add(String stdNo, int lecNo) throws Exception {
        return mvc.perform(post("/api/cart")
                .header(HttpHeaders.AUTHORIZATION, student(stdNo))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lecNo\": " + lecNo + "}"));
    }

    private ResultActions remove(String stdNo, int lecNo) throws Exception {
        return mvc.perform(delete("/api/cart/" + lecNo).header(HttpHeaders.AUTHORIZATION, student(stdNo)));
    }

    private String student(String stdNo) {
        return Bearer.of(jwtProvider, stdNo, Role.STUDENT);
    }
}
