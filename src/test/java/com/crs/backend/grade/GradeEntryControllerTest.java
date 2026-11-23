package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 성적 입력·수정(GRD-01). 시드 기준 시각 T0(2026-12-21)은 2026-2 성적 입력 기간입니다. 182번 데이터리터러시(교수 100002)는 입력중,
 * 수강생 12명(상대평가: A 한도 4명, A+B 한도 9명), 반영 비율 30/40/20/10 이고 지금 A 3명·B 4명이며 20260001 만 비어 있습니다.
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
                .andExpect(jsonPath("$.crsCd").value("1030018"))
                .andExpect(jsonPath("$.crsNm").value("데이터리터러시"))
                .andExpect(jsonPath("$.sectNo").value(1))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.evalMode").value("RELATIVE"))
                .andExpect(jsonPath("$.weights.midWt").value(30))
                .andExpect(jsonPath("$.weights.finWt").value(40))
                .andExpect(jsonPath("$.weights.hwWt").value(20))
                .andExpect(jsonPath("$.weights.attWt").value(10))
                .andExpect(jsonPath("$.ratio.enrolled").value(12))
                .andExpect(jsonPath("$.ratio.aCount").value(3))
                .andExpect(jsonPath("$.ratio.aLimit").value(4))
                .andExpect(jsonPath("$.ratio.abCount").value(7))
                .andExpect(jsonPath("$.ratio.abLimit").value(9))
                .andExpect(jsonPath("$.students", hasSize(12)))
                .andExpect(jsonPath("$.students[0].stdNo").value("20230001"))
                .andExpect(jsonPath("$.students[0].retake").value(false))
                .andExpect(jsonPath("$.students[0].midScr").value(99))
                .andExpect(jsonPath("$.students[0].finScr").value(97))
                .andExpect(jsonPath("$.students[0].hwScr").value(100))
                .andExpect(jsonPath("$.students[0].attScr").value(97))
                .andExpect(jsonPath("$.students[0].totScr").value(98))
                .andExpect(jsonPath("$.students[0].ltrGrd").value("A+"))
                .andExpect(jsonPath("$.students[0].grdPt").value(4.5))
                .andExpect(jsonPath("$.students[0].entered").value(true))
                .andExpect(jsonPath("$.students[8].stdNo").value(BLANK))
                .andExpect(jsonPath("$.students[8].totScr").value(nullValue()))
                .andExpect(jsonPath("$.students[8].grdPt").value(nullValue()))
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
    void 배정된_조교는_성적표를_본다_수강생이_없으면_절대평가_빈_목록() throws Exception {
        sheet(Student.TEACHING_ASSISTANT, Role.STUDENT, TA_LECTURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.ratio").value(nullValue()))
                .andExpect(jsonPath("$.students", hasSize(0)));
    }

    @Test
    void 그_강의의_수강생인_학생은_조교로_배정돼도_403() throws Exception {
        // 20240005 는 182번 수강생이다
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
        sheet("100003", Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        sheet(Account.ADMIN, Role.ADMIN, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
        mvc.perform(get(path(Lecture.DEMO_GRADE))).andExpect(status().isUnauthorized());
    }

    @Test
    void 평가_방식_교직은_A_50퍼센트만_전공_영어강의와_제외_강의는_절대평가() throws Exception {
        String crsCd = "1030018";
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = ?", crsCd);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("RELATIVE"))
                .andExpect(jsonPath("$.ratio.aLimit").value(6))
                .andExpect(jsonPath("$.ratio.abLimit").value(nullValue()));

        jdbc.update("UPDATE course SET cmpl_type = 'MAJOR_REQ' WHERE crs_cd = ?", crsCd);
        jdbc.update("UPDATE lecture SET eng_lec_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.ratio").value(nullValue()));

        jdbc.update("UPDATE lecture SET eng_lec_yn = 'N', rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"));
    }

    @Test
    void 수강인원이_9명_이하면_절대평가() throws Exception {
        jdbc.update(
                "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00'"
                        + " WHERE lec_no = ? AND std_no IN ('20260002', '20260003', '20260004')",
                Lecture.DEMO_GRADE);

        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.students", hasSize(9)));
    }

    // ── 저장 ──

    @Test
    void 점수를_저장하면_총점은_버림하고_등급을_계산한다() throws Exception {
        // (90×30 + 92×40 + 95×20 + 100×10) / 100 = 92.8 → 92 → A-
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 92, 95, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.ratio.aCount").value(4))
                .andExpect(jsonPath("$.students[8].totScr").value(92))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("A-"))
                .andExpect(jsonPath("$.students[8].grdPt").value(4.0))
                .andExpect(jsonPath("$.students[8].entered").value(true));

        assertThat(gradeRow(BLANK)).containsEntry("tot_scr", 92).containsEntry("ltr_grd", "A-");
    }

    @Test
    void 항목_점수는_소수_셋째_자리까지_받고_총점은_버림한다() throws Exception {
        // (90.125×30 + 92.5×40 + 95.75×20 + 100×10) / 100 = 93.1875 → 93 → A-
        String body = "{\"grades\":[{\"stdNo\":\"" + BLANK
                + "\",\"midScr\":90.125,\"finScr\":92.5,\"hwScr\":95.75,\"attScr\":100}]}";

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].midScr").value(90.125))
                .andExpect(jsonPath("$.students[8].finScr").value(92.5))
                .andExpect(jsonPath("$.students[8].hwScr").value(95.75))
                .andExpect(jsonPath("$.students[8].attScr").value(100))
                .andExpect(jsonPath("$.students[8].totScr").value(93))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("A-"));

        assertThat(jdbc.queryForObject(
                        "SELECT mid_scr FROM grade WHERE lec_no = ? AND std_no = ?",
                        BigDecimal.class,
                        Lecture.DEMO_GRADE,
                        BLANK))
                .isEqualByComparingTo("90.125");
    }

    @Test
    void 총점이_경계_바로_아래면_버림으로_아래_등급() throws Exception {
        // 모두 89.999 → 총점 89.999 → 89 → B+ (반올림하면 90, A- 가 된다)
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK
                                + "\",\"midScr\":89.999,\"finScr\":89.999,\"hwScr\":89.999,\"attScr\":89.999}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].totScr").value(89))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("B+"));
    }

    @Test
    void 일부_항목만_저장하면_총점과_등급은_비어_있다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].midScr").value(90))
                .andExpect(jsonPath("$.students[8].totScr").value(nullValue()))
                .andExpect(jsonPath("$.students[8].ltrGrd").value(nullValue()))
                .andExpect(jsonPath("$.students[8].entered").value(false));
    }

    @Test
    void 미입력_강의는_처음_저장할_때_성적_행을_만들고_입력중이_된다() throws Exception {
        // 158번은 미입력이라 성적 행이 하나도 없다
        int lecNo = Lecture.NOT_ENTERED;
        String stdNo = firstStudent(lecNo);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade WHERE lec_no = ?", Integer.class, lecNo))
                .isZero();

        save(professorOf(lecNo), Role.PROFESSOR, lecNo, scores(stdNo, 60, 60, 60, 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.students[0].stdNo").value(stdNo))
                .andExpect(jsonPath("$.students[0].ltrGrd").value("D-"));
    }

    @Test
    void 조교도_저장한다() throws Exception {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                Student.ON_LEAVE);

        save(Student.ON_LEAVE, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 80, 80, 80, 80))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].ltrGrd").value("B-"));
    }

    @Test
    void 재수강생은_총점이_높아도_A마이너스가_상한() throws Exception {
        jdbc.update("UPDATE enrollment SET retake_yn = 'Y' WHERE lec_no = ? AND std_no = ?", Lecture.DEMO_GRADE, BLANK);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 100, 100, 100, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].retake").value(true))
                .andExpect(jsonPath("$.students[8].totScr").value(100))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("A-"));
    }

    @Test
    void 제출된_강의를_고치면_입력중으로_돌아간다() throws Exception {
        jdbc.update("UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = ?", Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores("20230001", 99, 97, 100, 97))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"));
    }

    @Test
    void 상대평가_강의는_등급을_기준표보다_낮출_수_있다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "B+"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].totScr").value(95))
                .andExpect(jsonPath("$.students[8].ltrGrd").value("B+"))
                .andExpect(jsonPath("$.students[8].grdPt").value(3.5));
    }

    @Test
    void 기준표와_같은_등급은_어느_강의든_그대로_받는다() throws Exception {
        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "A0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[8].ltrGrd").value("A0"));
    }

    @Test
    void 등급을_높이거나_절대평가에서_바꾸거나_점수_없이_주면_409() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "A+"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_ADJUST_NOT_ALLOWED"));
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK + "\",\"midScr\":90,\"ltrGrd\":\"B0\"}]}")
                .andExpect(jsonPath("$.code").value("GRADE_ADJUST_NOT_ALLOWED"));

        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "B+"))
                .andExpect(jsonPath("$.code").value("GRADE_ADJUST_NOT_ALLOWED"));
    }

    @Test
    void 등급제_과목에_P를_넣거나_없는_등급을_넣으면_400() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "P"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_INPUT_MISMATCH"));
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted(BLANK, 95, "Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void PF_과목은_P_또는_F만_받는다() throws Exception {
        int lecNo = passFailLecture();
        String profNo = professorOf(lecNo);
        String stdNo = firstStudent(lecNo);

        save(profNo, Role.PROFESSOR, lecNo, "{\"grades\":[{\"stdNo\":\"" + stdNo + "\",\"ltrGrd\":\"F\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("PASS_FAIL"))
                .andExpect(jsonPath("$.weights").value(nullValue()))
                .andExpect(jsonPath("$.students[0].ltrGrd").value("F"))
                .andExpect(jsonPath("$.students[0].grdPt").value(nullValue()))
                .andExpect(jsonPath("$.students[0].entered").value(true));

        save(profNo, Role.PROFESSOR, lecNo, scores(stdNo, 90, 90, 90, 90))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_INPUT_MISMATCH"));
        save(profNo, Role.PROFESSOR, lecNo, "{\"grades\":[{\"stdNo\":\"" + stdNo + "\",\"ltrGrd\":\"A+\"}]}")
                .andExpect(jsonPath("$.code").value("GRADE_INPUT_MISMATCH"));
    }

    @Test
    void 점수가_0에서_100_정수가_아니거나_목록이_비었거나_학번이_중복이면_400() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 101, 90, 90, 90))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, -1, 90, 90, 90))
                .andExpect(status().isBadRequest());
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK + "\",\"midScr\":\"abc\"}]}")
                .andExpect(status().isBadRequest());
        save(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.DEMO_GRADE,
                        "{\"grades\":[{\"stdNo\":\"" + BLANK + "\",\"midScr\":92.1255}]}")
                .andExpect(status().isBadRequest());
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
        assertThat(gradeRow(BLANK)).containsEntry("mid_scr", null);
    }

    @Test
    void 성적_입력_기간이_아니면_409() throws Exception {
        TestClock.at(clock, TestClock.CART);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_PERIOD_CLOSED"));
        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
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
        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_CONFIRMED"));
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void 반영_비율이_없는_등급제_강의는_409() throws Exception {
        jdbc.update(
                "UPDATE lecture SET mid_wt = NULL, fin_wt = NULL, hw_wt = NULL, att_wt = NULL WHERE lec_no = ?",
                Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEIGHTS_NOT_SET"));
    }

    @Test
    void 폐강_강의는_409() throws Exception {
        // 폐강하면 신청인원이 0 이 된다(V3 ck_lecture_closed_no_enrl)
        jdbc.update("UPDATE lecture SET lec_stat = 'CLOSED', enrl_cnt = 0 WHERE lec_no = ?", Lecture.DEMO_GRADE);

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 90, 90, 90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    // ── 제출 ──

    @Test
    void 모두_입력하고_비율_안이면_제출된다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 92, 95, 100));

        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"))
                .andExpect(jsonPath("$.ratio.aCount").value(4))
                .andExpect(jsonPath("$.ratio.abCount").value(8));
        assertThat(grdStat(Lecture.DEMO_GRADE)).isEqualTo("SUBMITTED");
    }

    @Test
    void 비어_있는_학생이_있으면_제출할_수_없다() throws Exception {
        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_INCOMPLETE"));
    }

    @Test
    void A등급이_한도를_넘으면_제출할_수_없고_등급을_낮추면_제출된다() throws Exception {
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 95, 95, 95, 95));
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores("20260002", 95, 95, 95, 95));

        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_RATIO_EXCEEDED"));

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, adjusted("20260002", 95, "B+"));
        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());
    }

    @Test
    void A와_B_합이_한도를_넘으면_제출할_수_없다() throws Exception {
        // A 3명 + B 4명에 B 3명을 더하면 A+B 10명 > 한도 9명
        String body = "{\"grades\":[" + score(BLANK, 85, 85, 85, 85) + "," + score("20230002", 85, 85, 85, 85) + ","
                + score("20240003", 85, 85, 85, 85) + "]}";
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, body)
                .andExpect(jsonPath("$.ratio.abCount").value(10));

        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_RATIO_EXCEEDED"));
    }

    @Test
    void 이미_제출한_강의는_다시_제출할_수_없다() throws Exception {
        jdbc.update("UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = ?", Lecture.DEMO_GRADE);

        submit(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
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
        return jdbc.queryForObject(
                "SELECT MIN(std_no) FROM enrollment WHERE lec_no = ? AND enrl_stat = 'ENROLLED'", String.class, lecNo);
    }

    private Map<String, Object> gradeRow(String stdNo) {
        return jdbc.queryForMap(
                "SELECT mid_scr, tot_scr, ltr_grd FROM grade WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                stdNo);
    }

    private String grdStat(int lecNo) {
        return jdbc.queryForObject("SELECT grd_stat FROM lecture WHERE lec_no = ?", String.class, lecNo);
    }

    private static String score(String stdNo, Integer mid, Integer fin, Integer hw, Integer att) {
        return "{\"stdNo\":\"%s\",\"midScr\":%s,\"finScr\":%s,\"hwScr\":%s,\"attScr\":%s}"
                .formatted(stdNo, mid, fin, hw, att);
    }

    private static String scores(String stdNo, Integer mid, Integer fin, Integer hw, Integer att) {
        return "{\"grades\":[" + score(stdNo, mid, fin, hw, att) + "]}";
    }

    /** 네 항목을 모두 score 로 넣고 등급을 grade 로 지정 */
    private static String adjusted(String stdNo, int score, String grade) {
        return "{\"grades\":[{\"stdNo\":\"%s\",\"midScr\":%d,\"finScr\":%d,\"hwScr\":%d,\"attScr\":%d,\"ltrGrd\":\"%s\"}]}"
                .formatted(stdNo, score, score, score, score, grade);
    }

    private ResultActions sheet(String usrNo, Role role, int lecNo) throws Exception {
        return mvc.perform(get(path(lecNo)).header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private ResultActions save(String usrNo, Role role, int lecNo, String body) throws Exception {
        return mvc.perform(put(path(lecNo))
                .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions submit(String usrNo, Role role, int lecNo) throws Exception {
        return mvc.perform(
                post(path(lecNo) + "/submit").header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private static String path(int lecNo) {
        return "/api/teaching/lectures/" + lecNo + "/grades";
    }
}
