package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import com.crs.backend.support.TestClock;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 확정(GRD-02). 시드 기준 시각 T0(2026-12-21)은 2026-2 성적 입력 기간(12-14 09:00 ~ 12-23 18:00) 안이라 아직 확정할 수 없습니다.
 * 2026-2 개설 강의 31개 중 미입력 1(158번), 입력중 1(182번), 제출 29 입니다.
 */
@AutoConfigureMockMvc
@Transactional
class GradeConfirmControllerTest extends IntegrationTest {

    private static final String PATH = "/api/admin/grades";
    /** 2026-2 성적 입력 기간의 끝 */
    private static final String GRADE_END = "2026-12-23T18:00:00";
    /** 성적 입력 기간이 끝난 뒤 */
    private static final String AFTER_GRADE_END = "2026-12-24T10:00:00";

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

    // ── 성적상태 조회 ──

    @Test
    void 학기를_빼면_현재_학기의_성적상태를_본다() throws Exception {
        asAdmin(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND))
                .andExpect(jsonPath("$.yr").value(2026))
                .andExpect(jsonPath("$.semType").value("SECOND"))
                .andExpect(jsonPath("$.gradeEnd").value(GRADE_END))
                .andExpect(jsonPath("$.confirmable").value(false))
                .andExpect(jsonPath("$.counts.NOT_ENTERED").value(1))
                .andExpect(jsonPath("$.counts.ENTERING").value(1))
                .andExpect(jsonPath("$.counts.SUBMITTED").value(29))
                .andExpect(jsonPath("$.counts.CONFIRMED").value(0))
                .andExpect(jsonPath("$.lectures", hasSize(31)));
    }

    @Test
    void 미입력으로_거르면_담당_교수_연락처와_함께_미입력_강의만_나온다() throws Exception {
        Map<String, Object> professor = jdbc.queryForMap(
                "SELECT u.usr_no, u.usr_nm, u.email FROM lecture l JOIN user_account u ON u.usr_no = l.prof_no"
                        + " WHERE l.lec_no = ?",
                Lecture.NOT_ENTERED);

        asAdmin(get(PATH).param("semNo", String.valueOf(Semester.Y2026_SECOND)).param("grdStat", "NOT_ENTERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.SUBMITTED").value(29))
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].lecNo").value(Lecture.NOT_ENTERED))
                .andExpect(jsonPath("$.lectures[0].crsCd").value("1010016"))
                .andExpect(jsonPath("$.lectures[0].crsNm").value("소프트웨어공학"))
                .andExpect(jsonPath("$.lectures[0].sectNo").value(1))
                .andExpect(jsonPath("$.lectures[0].profNo").value(professor.get("usr_no")))
                .andExpect(jsonPath("$.lectures[0].profNm").value(professor.get("usr_nm")))
                .andExpect(jsonPath("$.lectures[0].profEmail").value(professor.get("email")))
                .andExpect(jsonPath("$.lectures[0].enrlCnt").value(3))
                .andExpect(jsonPath("$.lectures[0].grdStat").value("NOT_ENTERED"))
                .andExpect(jsonPath("$.lectures[0].cnfmDtm").value(nullValue()));
    }

    @Test
    void 폐강_강의는_목록과_개수에서_빠진다() throws Exception {
        int lecNo = submittedLectureWithoutStudents();
        jdbc.update("UPDATE lecture SET lec_stat = 'CLOSED' WHERE lec_no = ?", lecNo);

        asAdmin(get(PATH))
                .andExpect(jsonPath("$.counts.SUBMITTED").value(28))
                .andExpect(jsonPath("$.lectures", hasSize(30)));
    }

    @Test
    void 성적입력_일정이_없는_학기는_gradeEnd_가_없고_확정할_수_없다() throws Exception {
        jdbc.update("DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type = 'GRADE'", Semester.Y2026_SECOND);
        TestClock.at(clock, AFTER_GRADE_END);

        asAdmin(get(PATH))
                .andExpect(jsonPath("$.gradeEnd").value(nullValue()))
                .andExpect(jsonPath("$.confirmable").value(false));
        confirm(submittedLecture())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_NOT_OVER"));
    }

    @Test
    void 없는_학기는_404_상태값이_틀리면_400() throws Exception {
        asAdmin(get(PATH).param("semNo", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEMESTER_NOT_FOUND"));
        asAdmin(get(PATH).param("grdStat", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관리자가_아니면_403_토큰이_없으면_401() throws Exception {
        mvc.perform(get(PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                Bearer.of(jwtProvider, Account.DEMO_PROFESSOR, Role.PROFESSOR)))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/confirm")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                Bearer.of(jwtProvider, Account.DEMO_PROFESSOR, Role.PROFESSOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(submittedLecture())))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ── 확정 ──

    @Test
    void 성적_입력_기간이_끝나면_제출된_강의를_확정하고_확정자와_확정일시를_남긴다() throws Exception {
        int first = submittedLecture();
        int second = submittedLectureWithoutStudents();
        TestClock.at(clock, AFTER_GRADE_END);

        confirm(second, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed", hasSize(2)))
                .andExpect(jsonPath("$.confirmed[0].lecNo").value(Math.min(first, second)))
                .andExpect(jsonPath("$.confirmed[0].grdStat").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmed[0].cnfmDtm").value(AFTER_GRADE_END))
                .andExpect(jsonPath("$.confirmed[1].lecNo").value(Math.max(first, second)));

        Map<String, Object> row = jdbc.queryForMap("SELECT grd_stat, cnfm_emp_no FROM lecture WHERE lec_no = ?", first);
        assertThat(row).containsEntry("grd_stat", "CONFIRMED").containsEntry("cnfm_emp_no", Account.ADMIN);
        assertThat(jdbc.queryForObject("SELECT cnfm_dtm FROM lecture WHERE lec_no = ?", LocalDateTime.class, first))
                .isEqualTo(LocalDateTime.parse(AFTER_GRADE_END));

        asAdmin(get(PATH))
                .andExpect(jsonPath("$.confirmable").value(true))
                .andExpect(jsonPath("$.counts.SUBMITTED").value(27))
                .andExpect(jsonPath("$.counts.CONFIRMED").value(2));
    }

    @Test
    void 성적_입력_기간의_끝_시각부터_확정할_수_있다() throws Exception {
        int lecNo = submittedLecture();

        TestClock.at(clock, "2026-12-23T17:59:59");
        confirm(lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_NOT_OVER"));

        TestClock.at(clock, GRADE_END);
        confirm(lecNo).andExpect(status().isOk());
    }

    @Test
    void 성적_입력_기간_중에는_확정할_수_없다() throws Exception {
        confirm(submittedLecture())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_NOT_OVER"));
    }

    @Test
    void 미입력_입력중_강의는_확정할_수_없다() throws Exception {
        TestClock.at(clock, AFTER_GRADE_END);

        confirm(Lecture.NOT_ENTERED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_NOT_SUBMITTED"));
        confirm(Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_NOT_SUBMITTED"))
                .andExpect(jsonPath("$.detail").value("제출되지 않은 성적은 확정할 수 없습니다. (강의번호 182)"));
    }

    @Test
    void 이미_확정한_강의는_다시_확정할_수_없다() throws Exception {
        int lecNo = submittedLecture();
        TestClock.at(clock, AFTER_GRADE_END);
        confirm(lecNo).andExpect(status().isOk());

        confirm(lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CONFIRMED"));
    }

    @Test
    void 하나라도_확정할_수_없으면_아무것도_확정하지_않는다() throws Exception {
        int lecNo = submittedLecture();
        TestClock.at(clock, AFTER_GRADE_END);

        confirm(lecNo, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_NOT_SUBMITTED"));
        assertThat(jdbc.queryForObject("SELECT grd_stat FROM lecture WHERE lec_no = ?", String.class, lecNo))
                .isEqualTo("SUBMITTED");
    }

    @Test
    void 없는_강의는_404_폐강_강의는_409() throws Exception {
        TestClock.at(clock, AFTER_GRADE_END);
        confirm(999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));

        int lecNo = submittedLectureWithoutStudents();
        jdbc.update("UPDATE lecture SET lec_stat = 'CLOSED' WHERE lec_no = ?", lecNo);
        confirm(lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    @Test
    void 목록이_비었거나_중복이면_400() throws Exception {
        asAdmin(post(PATH + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"lecNos\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        confirm(Lecture.NOT_ENTERED, Lecture.NOT_ENTERED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ── 헬퍼 ──

    /** 2026-2 제출 강의 중 수강생이 있는 것. 시드를 다시 만들면 번호가 바뀌어서 조건으로 찾는다 */
    private int submittedLecture() {
        return jdbc.queryForObject(
                "SELECT MIN(lec_no) FROM lecture WHERE sem_no = ? AND grd_stat = 'SUBMITTED' AND enrl_cnt > 0",
                Integer.class,
                Semester.Y2026_SECOND);
    }

    /** 2026-2 제출 강의 중 수강생이 없는 것(폐강해도 V3 ck_lecture_closed_no_enrl 를 어기지 않음) */
    private int submittedLectureWithoutStudents() {
        return jdbc.queryForObject(
                "SELECT MIN(lec_no) FROM lecture WHERE sem_no = ? AND grd_stat = 'SUBMITTED' AND enrl_cnt = 0",
                Integer.class,
                Semester.Y2026_SECOND);
    }

    private ResultActions confirm(int... lecNos) throws Exception {
        return asAdmin(
                post(PATH + "/confirm").contentType(MediaType.APPLICATION_JSON).content(body(lecNos)));
    }

    private static String body(int... lecNos) {
        StringBuilder json = new StringBuilder("{\"lecNos\":[");
        for (int i = 0; i < lecNos.length; i++) {
            json.append(i == 0 ? "" : ",").append(lecNos[i]);
        }
        return json.append("]}").toString();
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(
                request.header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)));
    }
}
