package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import org.springframework.test.web.servlet.result.JsonPathResultMatchers;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 입력·수정(GRD-01). 교수·조교는 원점수만 저장하고, 제출할 때 원점수 총점(tot_scr) → 최종점수(fnl_scr) → 등급을 계산한다. 시드 기준
 * 시각 T0(2026-12-21)은 2026-2 성적 입력 기간이다. DEMO_GRADE(데이터리터러시, 교수 DEMO_PROFESSOR)는 입력중, 수강생 12명(상대평가: A구간
 * 4명, B구간 9위까지), 반영 비율 30/40/20/10 이고 Student.DEMO 만 원점수가 비어 있다.
 *
 * <p>학번·순위에 기대지 않도록, 제출 테스트는 학생들의 원점수 총점을 테스트에서 직접 정한다({@link #setTotals}).
 */
@AutoConfigureMockMvc
@Transactional
class GradeEntryControllerTest extends IntegrationTest {

    /** DEMO_GRADE 의 빈칸 학생 */
    private static final String BLANK = Student.DEMO;

    /**
     * 12명에게 줄 원점수 총점(높은 순). 경계(4·5위, 9·10위)에 동점이 없다. 기대 결과: A구간 98 A+·95 A0·92 A-·88→90 A-, B구간 87 B+·84
     * B0·82 B-·79→80 B-·78→80 B-, C·D·F구간 77 C+·74 C0·58 F.
     */
    private static final int[] TOTALS = {98, 95, 92, 88, 87, 84, 82, 79, 78, 77, 74, 58};

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
        String first = enrolledStudents(Lecture.DEMO_GRADE).get(0);
        Integer firstTotal = jdbc.queryForObject(
                "SELECT tot_scr FROM grade WHERE lec_no = ? AND std_no = ?", Integer.class, Lecture.DEMO_GRADE, first);

        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.crsCd").value(crsCdOf(Lecture.DEMO_GRADE)))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.evalMode").value("RELATIVE"))
                .andExpect(jsonPath("$.weights.midWt").value(30))
                .andExpect(jsonPath("$.weights.attWt").value(10))
                .andExpect(jsonPath("$.bands.enrolled").value(12))
                .andExpect(jsonPath("$.bands.aLimit").value(4))
                .andExpect(jsonPath("$.bands.abLimit").value(9))
                .andExpect(jsonPath("$.students", hasSize(12)))
                .andExpect(jsonPath("$.students[0].stdNo").value(first))
                .andExpect(student(first, "totScr").value(contains(firstTotal)))
                .andExpect(student(first, "entered").value(contains(true)))
                .andExpect(student(BLANK, "totScr").value(contains(nullValue())))
                .andExpect(student(BLANK, "entered").value(contains(false)));
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
        sheet(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.TA_CURRENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.bands").value(nullValue()))
                .andExpect(jsonPath("$.students", hasSize(0)));
    }

    @Test
    void 다른_교수와_관리자는_403_없는_강의는_404_토큰이_없으면_401() throws Exception {
        sheet(otherProfessor(Lecture.DEMO_GRADE), Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isForbidden());
        sheet(Account.ADMIN, Role.ADMIN, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
        mvc.perform(get(path(Lecture.DEMO_GRADE))).andExpect(status().isUnauthorized());
    }

    @Test
    void 평가_방식_교직은_A구간만_전공_영어강의와_제외_강의와_9명_이하는_절대평가() throws Exception {
        String crsCd = crsCdOf(Lecture.DEMO_GRADE);
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
        List<String> students = enrolledStudents(Lecture.DEMO_GRADE);
        for (String stdNo : students.subList(0, 3)) {
            jdbc.update(
                    "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00'"
                            + " WHERE lec_no = ? AND std_no = ?",
                    Lecture.DEMO_GRADE,
                    stdNo);
        }
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.evalMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$.students", hasSize(9)));
    }

    // ── 원점수 저장 ──

    @Test
    void 원점수를_저장하면_원점수_총점을_저장하고_강의_전체의_최종점수와_등급을_비운다() throws Exception {
        // (90×30 + 92×40 + 95×20 + 100×10) / 100 = 92.8 → 92
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 92, 95, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(student(BLANK, "totScr").value(contains(92)))
                .andExpect(student(BLANK, "entered").value(contains(true)))
                .andExpect(jsonPath("$.students[*].fnlScr", everyItem(nullValue())))
                .andExpect(jsonPath("$.students[*].ltrGrd", everyItem(nullValue())));

        assertThat(jdbc.queryForObject(
                        "SELECT tot_scr FROM grade WHERE lec_no = ? AND std_no = ?",
                        Integer.class,
                        Lecture.DEMO_GRADE,
                        BLANK))
                .isEqualTo(92);
    }

    @Test
    void 원점수는_소수_셋째_자리까지_받는다() throws Exception {
        // (90.125×30 + 92.5×40 + 95.75×20 + 100×10) / 100 = 93.1875 → 93
        save(body(BLANK, "\"midScr\":90.125,\"finScr\":92.5,\"hwScr\":95.75,\"attScr\":100"))
                .andExpect(status().isOk())
                .andExpect(student(BLANK, "midScr").value(contains(90.125)))
                .andExpect(student(BLANK, "finScr").value(contains(92.5)))
                .andExpect(student(BLANK, "attScr").value(contains(100)))
                .andExpect(student(BLANK, "totScr").value(contains(93)));

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
                .andExpect(student(BLANK, "midScr").value(contains(90)))
                .andExpect(student(BLANK, "totScr").value(contains(nullValue())))
                .andExpect(student(BLANK, "entered").value(contains(false)));
    }

    @Test
    void 미입력_강의는_처음_저장할_때_성적_행을_만든다() throws Exception {
        int lecNo = Lecture.NOT_ENTERED;
        String stdNo = enrolledStudents(lecNo).get(0);

        save(professorOf(lecNo), Role.PROFESSOR, lecNo, scores(stdNo, 60, 60, 60, 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(student(stdNo, "totScr").value(contains(60)));
    }

    @Test
    void 조교도_저장한다() throws Exception {
        // 조교 배정은 조교 모듈 일이라 테스트에서만 직접 넣는다(대학원생만 배정된다, V8)
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                Student.TEACHING_ASSISTANT);

        save(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 80, 80, 80, 80))
                .andExpect(status().isOk());
    }

    @Test
    void 제출된_강의를_고치면_결과를_비우고_입력중으로_돌아간다() throws Exception {
        setTotals(Lecture.DEMO_GRADE, TOTALS);
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 93, 93, 93, 93))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(jsonPath("$.students[*].fnlScr", everyItem(nullValue())))
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
    void 상대평가는_순위로_구간을_나눠_최종점수와_등급을_정하고_원점수_총점은_그대로_둔다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));

        // [원점수 총점, 최종점수, 등급]
        assertThat(result(ranked.get(0))).containsExactly(98, 98, "A+");
        assertThat(result(ranked.get(1))).containsExactly(95, 95, "A0");
        assertThat(result(ranked.get(3))).containsExactly(88, 90, "A-"); // A구간: 90 으로 올림
        assertThat(result(ranked.get(4))).containsExactly(87, 87, "B+");
        assertThat(result(ranked.get(7))).containsExactly(79, 80, "B-"); // B구간: 80 으로 올림
        assertThat(result(ranked.get(8))).containsExactly(78, 80, "B-");
        assertThat(result(ranked.get(9))).containsExactly(77, 77, "C+");
        assertThat(result(ranked.get(11))).containsExactly(58, 58, "F");
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(student(ranked.get(3), "totScr").value(contains(88)))
                .andExpect(student(ranked.get(3), "fnlScr").value(contains(90)))
                .andExpect(student(ranked.get(3), "ltrGrd").value(contains("A-")))
                .andExpect(student(ranked.get(3), "grdPt").value(contains(4.0)));
    }

    @Test
    void A구간에서_넘치는_높은_점수는_B구간_꼭대기인_89로_내린다() throws Exception {
        // 상위 6명이 모두 94 이상: 5·6위(A0 점수)는 B구간이라 89(B+)
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {99, 98, 97, 96, 95, 94, 85, 84, 83, 70, 65, 60});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(3))).containsExactly(96, 96, "A0");
        assertThat(result(ranked.get(4))).containsExactly(95, 89, "B+");
        assertThat(result(ranked.get(5))).containsExactly(94, 89, "B+");
        assertThat(result(ranked.get(9))).containsExactly(70, 70, "C-");
    }

    @Test
    void 완전_상대평가라_원점수가_낮아도_A구간은_90_B구간은_80으로_올린다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(0))).containsExactly(61, 90, "A-");
        assertThat(result(ranked.get(3))).containsExactly(58, 90, "A-");
        assertThat(result(ranked.get(4))).containsExactly(57, 80, "B-");
        assertThat(result(ranked.get(8))).containsExactly(53, 80, "B-");
        assertThat(result(ranked.get(9))).containsExactly(52, 52, "F");
    }

    @Test
    void 교직_과목은_A구간_50퍼센트_나머지는_89를_넘지_않고_기준표() throws Exception {
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = ?", crsCdOf(Lecture.DEMO_GRADE));
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(5))).containsExactly(84, 90, "A-"); // 6위까지 A구간
        assertThat(result(ranked.get(6))).containsExactly(82, 82, "B-"); // 그 아래는 기준표
        assertThat(result(ranked.get(7))).containsExactly(79, 79, "C+");
    }

    @Test
    void 교직_과목은_A구간_밖의_높은_점수를_89로_내린다() throws Exception {
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = ?", crsCdOf(Lecture.DEMO_GRADE));
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {99, 98, 97, 96, 95, 94, 93, 92, 70, 65, 60, 55});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(5))).containsExactly(94, 94, "A0");
        assertThat(result(ranked.get(6))).containsExactly(93, 89, "B+");
    }

    @Test
    void 구간_경계에_총점이_같으면_제출하지_않는다() throws Exception {
        // A/B 경계(4·5위)가 87 로 같음
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 87, 87, 84, 82, 79, 78, 77, 74, 58});
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(3))))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(4))));

        // B/C·D·F 경계(9·10위)가 77 로 같음
        ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 88, 87, 84, 82, 79, 77, 77, 74, 58});
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(8))));
    }

    @Test
    void 구간_안의_동점은_제출된다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 92, 92, 88, 87, 84, 84, 79, 78, 77, 74, 74});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(1))).containsExactly(92, 92, "A-");
        assertThat(result(ranked.get(2))).containsExactly(92, 92, "A-");
    }

    @Test
    void 재수강생은_A구간이어도_A마이너스가_상한() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);
        jdbc.update(
                "UPDATE enrollment SET retake_yn = 'Y' WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                ranked.get(0));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(0))).containsExactly(98, 98, "A-");
    }

    // ── 제출: 절대평가·P/F ──

    @Test
    void 절대평가는_원점수_총점을_그대로_기준표에_넣는다() throws Exception {
        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(3))).containsExactly(88, 88, "B+"); // 올리지 않음
        assertThat(result(ranked.get(7))).containsExactly(79, 79, "C+");
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
                .andExpect(student(students.get(0), "ltrGrd").value(contains("F")))
                .andExpect(student(students.get(0), "grdPt").value(contains(nullValue())));
        assertThat(result(lecNo, students.get(0))).containsExactly(89, 89, "F");
        for (String stdNo : students.subList(1, students.size())) {
            assertThat(result(lecNo, stdNo)).containsExactly(95, 95, "P");
        }
    }

    @Test
    void PF_과목도_비어_있는_학생이_있으면_제출할_수_없다() throws Exception {
        int lecNo = passFailLecture();
        String profNo = professorOf(lecNo);
        String stdNo = enrolledStudents(lecNo).get(0);
        jdbc.update("UPDATE lecture SET mid_wt = 0, fin_wt = 0, hw_wt = 0, att_wt = 100 WHERE lec_no = ?", lecNo);

        save(profNo, Role.PROFESSOR, lecNo, scores(stdNo, null, null, null, 95)).andExpect(status().isOk());

        submit(profNo, lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_INCOMPLETE"));
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
        jdbc.update("UPDATE lecture SET grd_stat = 'NOT_ENTERED' WHERE lec_no = ?", Lecture.TA_CURRENT);

        submit(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.TA_CURRENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));
    }

    // ── 헬퍼 ──

    /**
     * 수강생(학번 순)에게 원점수 총점을 차례로 줍니다(네 항목을 모두 그 점수로, 반영 비율 합이 100 이라 총점도 그 점수). totals 는 높은 순이라
     * 돌려주는 학번 목록이 곧 순위입니다.
     */
    private List<String> setTotals(int lecNo, int[] totals) {
        List<String> students = enrolledStudents(lecNo);
        assertThat(students).as("시드 수강생 수").hasSize(totals.length);
        for (int i = 0; i < totals.length; i++) {
            setRaw(lecNo, students.get(i), totals[i]);
        }
        return students;
    }

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

    /** 제출 뒤 [원점수 총점, 최종점수, 등급] */
    private List<Object> result(String stdNo) {
        return result(Lecture.DEMO_GRADE, stdNo);
    }

    private List<Object> result(int lecNo, String stdNo) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT tot_scr, fnl_scr, ltr_grd FROM grade WHERE lec_no = ? AND std_no = ?", lecNo, stdNo);
        return List.of(
                ((Number) row.get("tot_scr")).intValue(), ((Number) row.get("fnl_scr")).intValue(), row.get("ltr_grd"));
    }

    /** 성적표에서 학번으로 찾은 학생의 필드(정렬 위치에 기대지 않음). 결과는 한 개짜리 배열 */
    private static JsonPathResultMatchers student(String stdNo, String field) {
        return jsonPath("$.students[?(@.stdNo == '" + stdNo + "')]." + field);
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

    private String otherProfessor(int lecNo) {
        return jdbc.queryForObject(
                "SELECT MIN(prof_no) FROM professor WHERE prof_no <> ?", String.class, professorOf(lecNo));
    }

    private String crsCdOf(int lecNo) {
        return jdbc.queryForObject("SELECT crs_cd FROM lecture WHERE lec_no = ?", String.class, lecNo);
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
