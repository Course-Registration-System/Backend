package com.crs.backend.enrollment;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
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
    FakeSchedulePolicy schedulePolicy;

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
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

    private int firstLecture(String where) {
        return jdbc.queryForObject("""
                SELECT l.lec_no FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND %s
                   AND l.lec_no NOT IN (SELECT lec_no FROM enrollment WHERE std_no = ?)
                 ORDER BY l.lec_no LIMIT 1
                """.formatted(where), Integer.class, Semester.Y2027_FIRST, Student.DEMO);
    }

    @Test
    void 학기를_비우면_신청_대상_학기를_본다() throws Exception {
        schedulePolicy.at(Semester.Y2027_FIRST, false);
        timetable(Student.DEMO, Role.STUDENT, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(11));
    }

    @Test
    void 신청_대상_학기가_없으면_현재_학기를_본다() throws Exception {
        timetable(Student.DEMO, Role.STUDENT, "").andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND));
    }

    @Test
    void 취소와_폐강은_빼고_채플은_총_학점에만_넣는다() throws Exception {
        int chapel = firstLecture("c.cmpl_type = 'CHAPEL'");
        int cancelled = firstLecture("c.cmpl_type <> 'CHAPEL' AND l.lec_no <> " + Lecture.ONLINE);
        enroll(Student.DEMO, Lecture.ONLINE, "ENROLLED");
        enroll(Student.DEMO, chapel, "ENROLLED");
        enroll(Student.DEMO, cancelled, "CANCELLED");
        enroll(Student.DEMO, Lecture.CLOSED, "ENROLLED");

        BigDecimal total = jdbc.queryForObject("""
                SELECT SUM(c.crdt) FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ? AND l.lec_stat = 'OPEN'
                """, BigDecimal.class, Student.DEMO, Semester.Y2027_FIRST);
        BigDecimal chapelCrdt = jdbc.queryForObject(
                "SELECT c.crdt FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd WHERE l.lec_no = ?",
                BigDecimal.class,
                chapel);

        timetable(Student.DEMO, Role.STUDENT, "?semNo=11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures[*].lecNo", hasItem(Lecture.ONLINE)))
                .andExpect(jsonPath("$.lectures[*].lecNo", hasItem(chapel)))
                .andExpect(jsonPath("$.lectures[*].lecNo", not(hasItem(cancelled))))
                .andExpect(jsonPath("$.lectures[*].lecNo", not(hasItem(Lecture.CLOSED))))
                .andExpect(jsonPath("$.totalCredits").value(total.doubleValue()))
                .andExpect(
                        jsonPath("$.credits").value(total.subtract(chapelCrdt).doubleValue()))
                .andExpect(jsonPath("$.maxCredit").value(18));
    }

    @Test
    void 온라인_칸은_강의실_없이_수업유형으로_구분한다() throws Exception {
        enroll(Student.DEMO, Lecture.ONLINE, "ENROLLED");
        timetable(Student.DEMO, Role.STUDENT, "?semNo=11")
                .andExpect(jsonPath("$.lectures[?(@.lecNo == 222)].timeSlots[0].clsType")
                        .value("ONLINE"))
                .andExpect(jsonPath("$.lectures[?(@.lecNo == 222)].timeSlots[0].roomCd")
                        .value((Object) null))
                .andExpect(jsonPath("$.lectures[?(@.lecNo == 222)].profNm").isNotEmpty());
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
