package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 입력·수정(GRD-01). 교수·조교는 원점수만 저장하고, 제출할 때 원점수 총점 → 상대점수 → 등급을 계산한다. 시드 기준 시각 T0(2026-12-21)은
 * 2026-2 성적 입력 기간이다. 182번 데이터리터러시(교수 100002)는 입력중, 수강생 12명(상대평가: A구간 4명, B구간 9위까지), 반영 비율
 * 30/40/20/10 이고 20260001 만 원점수가 비어 있다. 나머지 11명의 원점수 총점은 98·98·91·89·86·84·82·79·78·78·74 다.
 */
@AutoConfigureMockMvc
@Transactional
class GradeEntryControllerTest extends IntegrationTest {

    /** 시드: 조교(20240005)가 배정된 2026-2 강의(교수 100003, 수강생 0명, 제출) */
    private static final int TA_LECTURE = 162;
    /** 182번의 빈칸 학생 */
    private static final String BLANK = Student.DEMO;

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

    // ── 성적표 조회 ──

    @Test
    void 교수는_성적표를_본다() throws Exception {
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.crsNm").value("데이터리터러시"))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.evalMode").value("RELATIVE"))
                .andExpect(jsonPath("$.weights.midWt").value(30))
                .andExpect(jsonPath("$.weights.attWt").value(10))
                .andExpect(jsonPath("$.bands.enrolled").value(12))
                .andExpect(jsonPath("$.bands.aLimit").value(4))
                .andExpect(jsonPath("$.bands.abLimit").value(9))
                .andExpect(jsonPath("$.students", hasSize(12)))
                .andExpect(jsonPath("$.students[0].stdNo").value("20230001"))
                .andExpect(jsonPath("$.students[0].midScr").value(99))
                .andExpect(jsonPath("$.students[0].rawTotal").value(98))
                .andExpect(jsonPath("$.students[0].entered").value(true))
                .andExpect(jsonPath("$.students[8].stdNo").value(BLANK))
                .andExpect(jsonPath("$.students[8].rawTotal").value(nullValue()))
                .andExpect(jsonPath("$.students[8].entered").value(false));
    }

    @Test
    void 성적_입력_기간이_아니어도_성적표는_보지만_editable_이_false() throws Exception {
        TestClock.at(clock, TestClock.CART);

        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void 배정된_조교는_성적표를_본다_수강생이_없으면_절대평가() throws Exception {
        sheet(Student.TEACHING_ASSISTANT, Role.STUDENT, TA_LECTURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.bands").value(nullValue()))
                .andExpect(jsonPath("$.students", hasSize(0)));
    }

    @Test
    void 그_강의의_수강생인_학생은_조교로_배정돼도_403() throws Exception {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                Student.TEACHING_ASSISTANT);

        sheet(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        save(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 100, 100, 100, 100))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_교수와_관리자는_403_없는_강의는_404_토큰이_없으면_401() throws Exception {
        sheet("100003", Role.PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        sheet(Account.ADMIN, Role.ADMIN, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
        mvc.perform(get(path(Lecture.DEMO_GRADE))).andExpect(status().isUnauthorized());
    }

    @Test
    void 평가_방식_교직은_A구간만_전공_영어강의와_제외_강의와_9명_이하는_절대평가() throws Exception {
        String crsCd = "1030018";
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = ?", crsCd);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("RELATIVE"))
                .andExpect(jsonPath("$.bands.aLimit").value(6))
                .andExpect(jsonPath("$.bands.abLimit").value(nullValue()));

        jdbc.update("UPDATE course SET cmpl_type = 'MAJOR_REQ' WHERE crs_cd = ?", crsCd);
        jdbc.update("UPDATE lecture SET eng_lec_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"));

        jdbc.update("UPDATE lecture SET eng_lec_yn = 'N', rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"));

        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'N' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        jdbc.update(
                "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00'"
                        + " WHERE lec_no = ? AND std_no IN ('20260002', '20260003', '20260004')",
                Lecture.DEMO_GRADE);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.students", hasSize(9)));
    }

    // ── 원점수 저장 ──

    @Test
    void 원점수를_저장하면_강의_전체의_상대점수와_등급을_비우고_입력중이_된다() throws Exception {
        // (90×30 + 92×40 + 95×20 + 100×10) / 100 = 92.8 → 92
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 92, 95, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.students[8].rawTotal").value(92))
                .andExpect(jsonPath("$.students[8].entered").value(true))
                .andExpect(jsonPath("$.students[*].totScr", everyItem(nullValue())))
                .andExpect(jsonPath("$.students[*].ltrGrd", everyItem(nullValue())));
    }

    @Test
    void 원점수는_소수_셋째_자리까지_받는다() throws Exception {
        // (90.125×30 + 92.5×40 + 95.75×20 + 100×10) / 100 = 93.1875 → 93
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK
                                + "\",\"midScr\":90.125,\"finScr\":92.5,\"hwScr\":95.75,\"attScr\":100}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].midScr").value(90.125))
                .andExpect(jsonPath("$.students[8].finScr").value(92.5))
                .andExpect(jsonPath("$.students[8].attScr").value(100))
                .andExpect(jsonPath("$.students[8].rawTotal").value(93));

        assertThat(jdbc.queryForObject(
                        "SELECT mid_scr FROM grade WHERE lec_no = ? AND std_no = ?",
                        BigDecimal.class,
                        Lecture.DEMO_GRADE,
                        BLANK))
                .isEqualByComparingTo("90.125");
    }

    @Test
    void 일부_항목만_저장하면_원점수_총점은_비어_있다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].midScr").value(90))
                .andExpect(jsonPath("$.students[8].rawTotal").value(nullValue()))
                .andExpect(jsonPath("$.students[8].entered").value(false));
    }

    @Test
    void 미입력_강의는_처음_저장할_때_성적_행을_만든다() throws Exception {
        int lecNo = Lecture.NOT_ENTERED;
        String stdNo = firstStudent(lecNo);

        save(professorOf(lecNo), Role.PROFESSOR, lecNo, scores(stdNo, 60, 60, 60, 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.students[0].rawTotal").value(60));
    }

    @Test
    void 조교도_저장한다() throws Exception {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                Student.ON_LEAVE);

        save(Student.ON_LEAVE, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 80, 80, 80, 80))
                .andExpect(status().isOk());
    }

    @Test
    void 제출된_강의를_고치면_결과를_비우고_입력중으로_돌아간다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 93, 93, 93, 93))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.students[*].ltrGrd", everyItem(nullValue())));
    }

    @Test
    void 점수_형식이_틀리거나_목록이_비었거나_학번이_중복이면_400() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 101, 90, 90, 90))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, -1, 90, 90, 90))
                .andExpect(status().isBadRequest());
        save(body(BLANK, "\"midScr\":\"abc\"")).andExpect(status().isBadRequest());
        save(body(BLANK, "\"midScr\":92.1255")).andExpect(status().isBadRequest());
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, "{\"grades\":[]}")
                .andExpect(status().isBadRequest());
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK + "\"},{\"stdNo\":\"" + BLANK + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수강생이_아닌_학번은_404_한_명이라도_실패하면_아무것도_저장하지_않는다() throws Exception {
        String body =
                "{\"grades\":[" + score(BLANK, 90, 90, 90, 90) + "," + score(Student.ON_LEAVE, 90, 90, 90, 90) + "]}";

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED_STUDENT"));
        assertThat(jdbc.queryForObject(
                        "SELECT mid_scr FROM grade WHERE lec_no = ? AND std_no = ?",
                        BigDecimal.class,
                        Lecture.DEMO_GRADE,
                        BLANK))
                .isNull();
    }

    @Test
    void 성적_입력_기간이_아니면_409() throws Exception {
        TestClock.at(clock, TestClock.CART);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_CLOSED"));
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_CLOSED"));
    }

    @Test
    void 확정된_강의는_409() throws Exception {
        jdbc.update(
                "UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = ?, cnfm_dtm = '2026-12-21 09:00:00'"
                        + " WHERE lec_no = ?",
                Account.ADMIN,
                Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CONFIRMED"));
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_CONFIRMED"));
    }

    @Test
    void 반영_비율이_없으면_409() throws Exception {
        jdbc.update(
                "UPDATE lecture SET mid_wt = NULL, fin_wt = NULL, hw_wt = NULL, att_wt = NULL WHERE lec_no = ?",
                Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEIGHTS_NOT_SET"));
    }

    @Test
    void 폐강_강의는_409() throws Exception {
        jdbc.update("UPDATE lecture SET lec_stat = 'CLOSED', enrl_cnt = 0 WHERE lec_no = ?", Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    // ── 제출: 상대평가 ──

    @Test
    void 상대평가는_순위로_구간을_나눠_상대점수와_등급을_정한다() throws Exception {
        // 20260001 = 92 → 순위 98·98·92·91 | 89·86·84·82·79 | 78·78·74
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));

        assertThat(result("20230001")).isEqualTo(List.of(98, "A+")); // A구간
        assertThat(result(BLANK)).isEqualTo(List.of(92, "A-"));
        assertThat(result("20240001")).isEqualTo(List.of(91, "A-"));
        assertThat(result("20260002")).isEqualTo(List.of(89, "B+")); // B구간
        assertThat(result("20250001")).isEqualTo(List.of(80, "B-")); // B구간 9위: 79 → 80
        assertThat(result("20240003")).isEqualTo(List.of(78, "C+")); // C·D·F 구간
        assertThat(result("20230002")).isEqualTo(List.of(74, "C0"));
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.students[8].totScr").value(92))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("A-"))
                .andExpect(jsonPath("$.students[8].grdPt").value(4.0));
    }

    @Test
    void 완전_상대평가라_원점수가_낮아도_A구간은_90_B구간은_80으로_올린다() throws Exception {
        // 12명 원점수 50~61 → 상위 4명 A-(90), 다음 5명 B-(80), 나머지는 원점수 그대로 F
        List<String> students = enrolledStudents(Lecture.DEMO_GRADE);
        for (int i = 0; i < students.size(); i++) {
            setRaw(Lecture.DEMO_GRADE, students.get(i), 61 - i);
        }

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(students.get(0))).isEqualTo(List.of(90, "A-"));
        assertThat(result(students.get(3))).isEqualTo(List.of(90, "A-"));
        assertThat(result(students.get(4))).isEqualTo(List.of(80, "B-"));
        assertThat(result(students.get(8))).isEqualTo(List.of(80, "B-"));
        assertThat(result(students.get(9))).isEqualTo(List.of(52, "F"));
    }

    @Test
    void 교직_과목은_A구간_50퍼센트_나머지는_89를_넘지_않고_기준표() throws Exception {
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = '1030018'");
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        // 6위까지 A구간: 98·98·92·91·89·86 → 6위 86 은 90(A-)
        assertThat(result("20240004")).isEqualTo(List.of(90, "A-"));
        // 7위 84 는 그대로 B0, 9위 79 는 C+
        assertThat(result("20240005")).isEqualTo(List.of(84, "B0"));
        assertThat(result("20250001")).isEqualTo(List.of(79, "C+"));
    }

    @Test
    void 구간_경계에_총점이_같으면_제출하지_않는다() throws Exception {
        // 20260001 = 89 → 4위·5위가 89 로 A/B 경계에 걸림
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 89, 89, 89, 89));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString("20260001, 20260002")));

        // B/C 경계(9위·10위)도 같다: 20240003 을 79 로
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));
        setRaw(Lecture.DEMO_GRADE, "20240003", 79);
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString("20240003, 20250001")));
    }

    @Test
    void 재수강생은_A구간이어도_A마이너스가_상한() throws Exception {
        jdbc.update(
                "UPDATE enrollment SET retake_yn = 'Y' WHERE lec_no = ? AND std_no = '20230001'", Lecture.DEMO_GRADE);
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result("20230001")).isEqualTo(List.of(98, "A-"));
    }

    // ── 제출: 절대평가·P/F ──

    @Test
    void 절대평가는_원점수_총점을_그대로_기준표에_넣는다() throws Exception {
        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 92, 92, 92, 92));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result("20250001")).isEqualTo(List.of(79, "C+")); // 올리지 않음
        assertThat(result("20240003")).isEqualTo(List.of(78, "C+"));
    }

    @Test
    void PF_과목은_반영_비율로_총점을_내고_90_이상이면_P() throws Exception {
        int lecNo = passFailLecture();
        String profNo = professorOf(lecNo);
        List<String> students = enrolledStudents(lecNo);

        save(profNo, Role.PROFESSOR, lecNo, scores(students.get(0), 95, 95, 95, 95))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEIGHTS_NOT_SET"));

        jdbc.update("UPDATE lecture SET mid_wt = 0, fin_wt = 0, hw_wt = 0, att_wt = 100 WHERE lec_no = ?", lecNo);
        students.forEach(stdNo -> setRaw(lecNo, stdNo, 95));
        save(profNo, Role.PROFESSOR, lecNo, scores(students.get(0), 0, 0, 0, 89))
                .andExpect(status().isOk());

        submit(profNo, lecNo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("PASS_FAIL"))
                .andExpect(jsonPath("$.students[0].ltrGrd").value("F"))
                .andExpect(jsonPath("$.students[0].grdPt").value(nullValue()));
        if (students.size() > 1) {
            assertThat(result(lecNo, students.get(1))).isEqualTo(List.of(95, "P"));
        }
        assertThat(result(lecNo, students.get(0))).isEqualTo(List.of(89, "F"));
    }

    // ── 제출: 상태 ──

    @Test
    void 비어_있는_학생이_있으면_제출할_수_없다() throws Exception {
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_INCOMPLETE"));
    }

    @Test
    void 이미_제출한_강의는_다시_제출할_수_없다() throws Exception {
        jdbc.update("UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = ?", Lecture.DEMO_GRADE);

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_ALREADY_SUBMITTED"));
    }

    @Test
    void 수강생이_없는_강의는_미입력에서_바로_제출한다() throws Exception {
        jdbc.update("UPDATE lecture SET grd_stat = 'NOT_ENTERED' WHERE lec_no = ?", TA_LECTURE);

        submit(Student.TEACHING_ASSISTANT, Role.STUDENT, TA_LECTURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));
    }

    // ── 헬퍼 ──

    /** 학생의 네 항목 원점수를 모두 score 로(반영 비율 합이 100 이라 원점수 총점도 score) */
    private void setRaw(int lecNo, String stdNo, int score) {
        jdbc.update(
                "UPDATE grade SET mid_scr = ?, fin_scr = ?, hw_scr = ?, att_scr = ? WHERE lec_no = ? AND std_no = ?",
                score,
                score,
                score,
                score,
                lecNo,
                stdNo);
    }

    /** 제출 뒤 [상대점수, 등급] */
    private List<Object> result(String stdNo) {
        return result(Lecture.DEMO_GRADE, stdNo);
    }

    private List<Object> result(int lecNo, String stdNo) {
        Map<String, Object> row =
                jdbc.queryForMap("SELECT tot_scr, ltr_grd FROM grade WHERE lec_no = ? AND std_no = ?", lecNo, stdNo);
        return List.of(((Number) row.get("tot_scr")).intValue(), row.get("ltr_grd"));
    }

    private List<String> enrolledStudents(int lecNo) {
        return jdbc.queryForList(
                "SELECT std_no FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED' ORDER BY std_no",
                String.class,
                lecNo);
    }

    /** 2026-2 P/F 과목 강의 중 수강생이 있는 것. 시드를 다시 만들면 번호가 바뀌어서 조건으로 찾는다 */
    private int passFailLecture() {
        return jdbc.queryForObject("""
                SELECT MIN(l.lec_no) FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND c.eval_type = 'PASS_FAIL' AND l.lec_stat = 'OPEN'
                   AND EXISTS (SELECT 1 FROM enrollment e WHERE e.lec_no = l.lec_no AND e.enrl_stat = 'ENROLLED')
                """, Integer.class, Semester.Y2026_SECOND);
    }

    private String professorOf(int lecNo) {
        return jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, lecNo);
    }

    private String firstStudent(int lecNo) {
        return enrolledStudents(lecNo).get(0);
    }

    private static String score(String stdNo, Integer mid, Integer fin, Integer hw, Integer att) {
        return "{\"stdNo\":\"%s\",\"midScr\":%s,\"finScr\":%s,\"hwScr\":%s,\"attScr\":%s}"
                .formatted(stdNo, mid, fin, hw, att);
    }

    private static String scores(String stdNo, Integer mid, Integer fin, Integer hw, Integer att) {
        return "{\"grades\":[" + score(stdNo, mid, fin, hw, att) + "]}";
    }

    private static String body(String stdNo, String fields) {
        return "{\"grades\":[{\"stdNo\":\"" + stdNo + "\"," + fields + "}]}";
    }

    private ResultActions sheet(String usrNo, Role role, int lecNo) throws Exception {
        return mvc.perform(get(path(lecNo)).header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private ResultActions save(String body) throws Exception {
        return save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, body);
    }

    private ResultActions save(String usrNo, Role role, int lecNo, String body) throws Exception {
        return mvc.perform(put(path(lecNo))
                .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions submit(String profNo, int lecNo) throws Exception {
        return submit(profNo, Role.PROFESSOR, lecNo);
    }

    private ResultActions submit(String usrNo, Role role, int lecNo) throws Exception {
        return mvc.perform(
                post(path(lecNo) + "/submit").header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private static String path(int lecNo) {
        return "/api/teaching/lectures/" + lecNo + "/grades";
    }
}
