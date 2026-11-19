package com.crs.backend.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 2027-1 수강신청일 T1(2027-02-11). 수강신청이 시작돼 강의시간은 잠겼고, 정정 기간 전이라 폐강은 됩니다. */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "crs.clock.fixed-at=" + SeedTime.T1)
class LectureAdminAtEnrollmentTest extends IntegrationTest {

    // 198번 컴파일러의 지금 강의시간 그대로
    private static final String COMPILER = """
            {"profNo": "100002", "cap": %d, "engLec": false, "relGrdExmp": false,
             "timeSlots": [
               {"dow": "TUE", "startTm": "%s", "endTm": "10:15", "roomCd": "21301"},
               {"dow": "THU", "startTm": "09:00", "endTm": "10:15", "roomCd": "21301"}]}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    String admin;

    @BeforeEach
    void setUp() {
        SeedTime.atT1(dataSource);
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @Test
    void 수강신청이_시작되면_강의시간은_바꿀_수_없다() throws Exception {
        update(Lecture.ONE_SEAT_LEFT, COMPILER.formatted(4, "09:30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIME_CHANGE_CLOSED"));
    }

    @Test
    void 강의시간을_그대로_두면_정원은_늘릴_수_있다() throws Exception {
        update(Lecture.ONE_SEAT_LEFT, COMPILER.formatted(10, "09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cap").value(10));
    }

    @Test
    void 정원을_신청인원보다_줄일_수_없다() throws Exception {
        int enrolled =
                jdbc.queryForObject("SELECT enrl_cnt FROM lecture WHERE lec_no = ?", Integer.class, Lecture.FULL);
        assertThat(enrolled).isPositive();

        // 191번 컴퓨터구조(교수 100001, 정보관 303호 화·목 10:30~11:45)의 강의시간은 그대로 두고 정원만 줄인다
        String body = """
                {"profNo": "100001", "cap": %d, "engLec": false, "relGrdExmp": false,
                 "timeSlots": [
                   {"dow": "TUE", "startTm": "10:30", "endTm": "11:45", "roomCd": "21303"},
                   {"dow": "THU", "startTm": "10:30", "endTm": "11:45", "roomCd": "21303"}]}
                """.formatted(enrolled - 1);
        update(Lecture.FULL, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_ENROLLED"));
    }

    @Test
    void 폐강하면_신청은_모두_취소되고_장바구니에서_빠진다() throws Exception {
        int lecNo = Lecture.FULL;
        assertThat(countEnrolled(lecNo)).isPositive();
        assertThat(countCarts(lecNo)).isPositive();

        mvc.perform(patch("/api/admin/lectures/" + lecNo + "/close").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecStat").value("CLOSED"))
                .andExpect(jsonPath("$.enrlCnt").value(0));

        assertThat(countEnrolled(lecNo)).isZero();
        assertThat(countCarts(lecNo)).isZero();
        Integer cancelledByAdmin = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enrollment
                 WHERE lec_no = ? AND enrl_stat = 'CANCELLED' AND proc_type = 'ADMIN' AND cncl_dtm = ?
                """, Integer.class, lecNo, SeedTime.T1.replace('T', ' '));
        assertThat(cancelledByAdmin).isPositive();
    }

    private int countEnrolled(int lecNo) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED'", Integer.class, lecNo);
    }

    private int countCarts(int lecNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM course_cart WHERE lec_no = ?", Integer.class, lecNo);
    }

    private ResultActions update(int lecNo, String json) throws Exception {
        return mvc.perform(put("/api/admin/lectures/" + lecNo)
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
