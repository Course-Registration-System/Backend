package com.crs.backend.lecture;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수강생 명단 조회(LEC-02). 시드 기준 시각은 T0(2026-12-21)이고, 2026-2 는 신청 종료(정정 종료 2026-09-08 17:00) 뒤, 2027-1 은 신청
 * 종료(정정 종료 2027-03-09 17:00) 전입니다.
 */
@AutoConfigureMockMvc
@Transactional
class TeachingStudentControllerTest extends IntegrationTest {

    /** 시드: 조교(20240005)가 2026-2 에 배정된 강의 */
    private static final int TA_CURRENT_LECTURE = 162;
    /** 2027-1 신청 종료(정정 종료) */
    private static final String Y2027_FIRST_REGISTRATION_END = "2027-03-09T17:00:00";
    /** 2027-1 마지막 수강신청일 종료 */
    private static final String Y2027_FIRST_LAST_ENROLL_END = "2027-02-12T17:00:00";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    @Test
    void 교수는_신청_종료_뒤에_담당_강의의_수강생을_학번_순으로_본다() throws Exception {
        List<String> enrolled = jdbc.queryForList(
                "SELECT std_no FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED' ORDER BY std_no",
                String.class,
                Lecture.DEMO_GRADE);
        Map<String, Object> first = jdbc.queryForMap("""
                SELECT u.usr_nm, d.dept_nm, s.yr_lvl, u.email
                  FROM student s
                  JOIN user_account u ON u.usr_no = s.std_no
                  JOIN department d ON d.dept_no = s.dept_no
                 WHERE s.std_no = ?
                """, enrolled.get(0));

        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.students", hasSize(12)))
                .andExpect(jsonPath("$.students[*].stdNo", contains(enrolled.toArray())))
                .andExpect(jsonPath("$.students[0].usrNm").value(first.get("usr_nm")))
                .andExpect(jsonPath("$.students[0].deptNm").value(first.get("dept_nm")))
                .andExpect(jsonPath("$.students[0].yrLvl").value(first.get("yr_lvl")))
                .andExpect(jsonPath("$.students[0].email").value(first.get("email")));
    }

    @Test
    void 수강을_취소한_학생은_명단에서_빠진다() throws Exception {
        String cancelled = jdbc.queryForObject(
                "SELECT MIN(std_no) FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED'",
                String.class,
                Lecture.DEMO_GRADE);
        jdbc.update(
                "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00' WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                cancelled);

        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students", hasSize(11)))
                .andExpect(jsonPath("$.students[*].stdNo", not(hasItem(cancelled))));
    }

    @Test
    void 수강생이_없으면_빈_명단이다() throws Exception {
        jdbc.update(
                "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00' WHERE lec_no = ?",
                Lecture.DEMO_GRADE);

        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students", hasSize(0)));
    }

    @Test
    void 배정된_조교도_명단을_본다() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, TA_CURRENT_LECTURE).andExpect(status().isOk());
    }

    @Test
    void 그_강의의_수강생인_조교는_403() throws Exception {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) "
                        + "SELECT lec_no, std_no FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED' LIMIT 1",
                Lecture.DEMO_GRADE);
        String enrolledAssistant = jdbc.queryForObject(
                "SELECT std_no FROM teaching_assistant_assignment WHERE lec_no = ?", String.class, Lecture.DEMO_GRADE);

        as(enrolledAssistant, Role.STUDENT, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
    }

    @Test
    void 다른_교수_배정되지_않은_학생_관리자는_403() throws Exception {
        String otherProfessor = jdbc.queryForObject(
                "SELECT MIN(prof_no) FROM professor WHERE prof_no <> ?", String.class, Account.DEMO_PROFESSOR);

        as(otherProfessor, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        as(Account.ADMIN, Role.ADMIN, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
    }

    @Test
    void 없는_강의는_권한보다_먼저_404() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    @Test
    void 강의번호가_숫자가_아니면_400() throws Exception {
        mvc.perform(get("/api/teaching/lectures/abc/students")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                Bearer.of(jwtProvider, Account.DEMO_PROFESSOR, Role.PROFESSOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get(path(Lecture.DEMO_GRADE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 신청_종료_전에는_409() throws Exception {
        int lecNo = nextSemesterLecture();

        asProfessorOf(lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROSTER_NOT_OPEN"));
    }

    @Test
    void 신청_종료_시각부터_볼_수_있다() throws Exception {
        int lecNo = nextSemesterLecture();

        TestClock.at(clock, "2027-03-09T16:59:59");
        asProfessorOf(lecNo).andExpect(status().isConflict());

        TestClock.at(clock, Y2027_FIRST_REGISTRATION_END);
        asProfessorOf(lecNo).andExpect(status().isOk());
    }

    @Test
    void 정정_일정이_없는_학기는_마지막_수강신청일_종료부터_볼_수_있다() throws Exception {
        int lecNo = nextSemesterLecture();
        jdbc.update("DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'", Semester.Y2027_FIRST);

        TestClock.at(clock, "2027-02-12T16:59:59");
        asProfessorOf(lecNo).andExpect(status().isConflict());

        TestClock.at(clock, Y2027_FIRST_LAST_ENROLL_END);
        asProfessorOf(lecNo).andExpect(status().isOk());
    }

    @Test
    void 신청_일정이_없는_학기는_바로_볼_수_있다() throws Exception {
        int lecNo = nextSemesterLecture();
        jdbc.update(
                "DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('ENROLL', 'ADJUST')",
                Semester.Y2027_FIRST);

        asProfessorOf(lecNo).andExpect(status().isOk());
    }

    /** 2027-1 개설 강의 하나. 시드를 다시 만들면 번호가 바뀌어서 학기로 찾는다 */
    private int nextSemesterLecture() {
        return jdbc.queryForObject(
                "SELECT MIN(lec_no) FROM lecture WHERE sem_no = ? AND lec_stat = 'OPEN'",
                Integer.class,
                Semester.Y2027_FIRST);
    }

    private ResultActions asProfessorOf(int lecNo) throws Exception {
        String profNo = jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, lecNo);
        return as(profNo, Role.PROFESSOR, lecNo);
    }

    private ResultActions as(String usrNo, Role role, int lecNo) throws Exception {
        return mvc.perform(get(path(lecNo)).header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private static String path(int lecNo) {
        return "/api/teaching/lectures/" + lecNo + "/students";
    }
}
