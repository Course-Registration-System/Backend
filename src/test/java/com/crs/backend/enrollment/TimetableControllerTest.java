package com.crs.backend.enrollment;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.crs.backend.support.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class TimetableControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    /** 2027-1 채플(0.5학점, P/F). 시드를 다시 만들면 번호가 바뀌어서 이수구분으로 찾는다 */
    int chapel;

    @BeforeEach
    void setUp() {
        chapel = jdbc.queryForObject("""
                SELECT l.lec_no FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND c.cmpl_type = 'CHAPEL'
                """, Integer.class, Semester.Y2027_FIRST);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    private ResultActions timetable(String usrNo, Role role, String query) throws Exception {
        return mvc.perform(
                get("/api/me/timetable" + query).header("Authorization", Bearer.of(jwtProvider, usrNo, role)));
    }

    private void enroll(String stdNo, int lecNo, String enrlStat) {
        jdbc.update("""
                INSERT INTO enrollment (std_no, lec_no, enrl_stat, retake_yn, proc_type, enrl_dtm, cncl_dtm)
                VALUES (?, ?, ?, 'N', 'STUDENT', '2027-02-11 10:00:00',
                        IF(? = 'CANCELLED', '2027-02-11 11:00:00', NULL))
                ON DUPLICATE KEY UPDATE enrl_stat = VALUES(enrl_stat), cncl_dtm = VALUES(cncl_dtm)
                """, stdNo, lecNo, enrlStat, enrlStat);
    }

    @Test
    void 학기를_비우면_신청_대상_학기를_본다() throws Exception {
        TestClock.at(clock, TestClock.CART); // 현재 학기는 2026-2
        timetable(Student.DEMO, Role.STUDENT, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(11));
    }

    @Test
    void 신청_대상_학기가_없으면_현재_학기를_본다() throws Exception {
        TestClock.at(clock, "2027-03-09T17:00:00"); // 2027-1 정정이 끝난 순간
        timetable(Student.DEMO, Role.STUDENT, "").andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND));
    }

    @Test
    void 취소한_강의와_폐강_처리한_강의는_빼고_채플은_총_학점에만_넣는다() throws Exception {
        // T0 에 시연 학생은 2027-1 신청이 없다. 온라인 교양(3학점)과 채플(0.5학점)만 남아야 한다
        enroll(Student.DEMO, Lecture.ONLINE, "ENROLLED");
        enroll(Student.DEMO, chapel, "ENROLLED");
        enroll(Student.DEMO, Lecture.ONE_SEAT_LEFT, "CANCELLED");
        enroll(Student.DEMO, Lecture.FULL, "ENROLLED");
        mvc.perform(patch("/api/admin/lectures/" + Lecture.FULL + "/close")
                        .header("Authorization", Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)))
                .andExpect(status().isOk());

        timetable(Student.DEMO, Role.STUDENT, "?semNo=11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures[*].lecNo", containsInAnyOrder(Lecture.ONLINE, chapel)))
                .andExpect(jsonPath("$.totalCredits").value(3.5))
                .andExpect(jsonPath("$.credits").value(3.0))
                .andExpect(jsonPath("$.maxCredit").value(18));
    }

    @Test
    void 온라인_칸은_강의실_없이_수업유형으로_구분한다() throws Exception {
        enroll(Student.DEMO, Lecture.ONLINE, "ENROLLED");
        timetable(Student.DEMO, Role.STUDENT, "?semNo=11")
                .andExpect(jsonPath("$.lectures[?(@.lecNo == " + Lecture.ONLINE + ")].timeSlots[0].clsType")
                        .value("ONLINE"))
                .andExpect(jsonPath("$.lectures[?(@.lecNo == " + Lecture.ONLINE + ")].timeSlots[0].roomCd")
                        .value((Object) null))
                .andExpect(jsonPath("$.lectures[?(@.lecNo == " + Lecture.ONLINE + ")].profNm")
                        .isNotEmpty());
    }

    @Test
    void 학생이_아니면_거부한다() throws Exception {
        timetable(Account.ADMIN, Role.ADMIN, "?semNo=11").andExpect(status().isForbidden());
    }

    @Test
    void 없는_학기는_404() throws Exception {
        timetable(Student.DEMO, Role.STUDENT, "?semNo=999").andExpect(status().isNotFound());
    }
}
