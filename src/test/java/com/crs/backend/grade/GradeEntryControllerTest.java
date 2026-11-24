package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
 * 성적 입력·수정(GRD-01). 교수·조교는 등급제 과목은 원점수, P/F 과목은 P·F 만 저장한다. 등급제는 저장할 때마다(제출 때도) 원점수 총점
 * (tot_scr) → 최종점수(fnl_scr) → 등급을 다시 계산한다. 시드 기준 시각 T0(2026-12-21)은 2026-2 성적 입력 기간이다. DEMO_GRADE(데이터리터러시,
 * 교수 DEMO_PROFESSOR)는 입력중, 수강생 12명(상대평가: A 구역 4위, B 구역 9위까지), 반영 비율 30/40/20/10 이고 Student.DEMO 만 원점수가
 * 비어 있다.
 *
 * <p>학번·순위에 기대지 않도록, 제출 테스트는 학생들의 원점수 총점을 테스트에서 직접 정한다({@link #setTotals}).
 */
@AutoConfigureMockMvc
@Transactional
class GradeEntryControllerTest extends IntegrationTest {

    /** DEMO_GRADE 의 빈칸 학생 */
    private static final String BLANK = Student.DEMO;

    /**
     * 12명에게 줄 원점수 총점(높은 순). 경계(4·5위, 9·10위)에 동점이 없다. 기대 결과(구역 상한만, 올리지 않음): A 구역 98 A+·95 A0·92 A-·88
     * B+, B 구역 87 B+·84 B0·82 B-·79 C+·78 C+, 나머지 77 C+·74 C0·58 F.
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
    void 그_강의의_수강생인_학생은_조교로_배정돼도_403() throws Exception {
        String enrolledStudent = enrolledStudents(Lecture.DEMO_GRADE).get(0);
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                enrolledStudent);

        sheet(enrolledStudent, Role.STUDENT, Lecture.DEMO_GRADE).andExpect(status().isForbidden());
        save(enrolledStudent, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 100, 100, 100, 100))
                .andExpect(status().isForbidden());
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
    void 평가_방식_교직은_A구역만_전공_영어강의와_제외_강의와_9명_이하는_절대평가() throws Exception {
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
    void 원점수를_저장하면_원점수_총점을_저장하고_강의_전체의_최종점수와_등급을_다시_계산한다() throws Exception {
        // (90×30 + 92×40 + 95×20 + 100×10) / 100 = 92.8 → 92
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, 92, 95, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("ENTERING"))
                .andExpect(student(BLANK, "totScr").value(contains(92)))
                .andExpect(student(BLANK, "entered").value(contains(true)))
                .andExpect(jsonPath("$.students[*].fnlScr", everyItem(notNullValue())))
                .andExpect(jsonPath("$.students[*].ltrGrd", everyItem(notNullValue())));

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
    void 일부_항목만_저장하면_원점수_총점과_최종점수는_비어_있고_나머지_학생만_계산한다() throws Exception {
        String other = enrolledStudents(Lecture.DEMO_GRADE).stream()
                .filter(s -> !s.equals(BLANK))
                .findFirst()
                .orElseThrow();

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(BLANK, 90, null, null, null))
                .andExpect(status().isOk())
                .andExpect(student(BLANK, "midScr").value(contains(90)))
                .andExpect(student(BLANK, "totScr").value(contains(nullValue())))
                .andExpect(student(BLANK, "fnlScr").value(contains(nullValue())))
                .andExpect(student(BLANK, "entered").value(contains(false)))
                .andExpect(student(other, "fnlScr").value(contains(notNullValue())));
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
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.DEMO_GRADE,
                Student.ON_LEAVE);

        save(Student.ON_LEAVE, Role.STUDENT, Lecture.DEMO_GRADE, scores(BLANK, 80, 80, 80, 80))
                .andExpect(status().isOk());
    }

    @Test
    void 제출된_강의를_고치면_입력중으로_돌아가고_새_석차로_다시_계산한다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 91, 87, 84, 82, 79, 78, 77, 74, 58});
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));
        assertThat(result(ranked.get(3))).containsExactly(91, 91, "A-");

        // 꼴찌(58)를 99 로 고치면 1등이 되어 4위(91)는 5위로 밀려 B 구역 상한 89
        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(ranked.get(11), 99, 99, 99, 99))
                .andExpect(jsonPath("$.grdStat").value("ENTERING"));
        assertThat(result(ranked.get(11))).containsExactly(99, 99, "A+");
        assertThat(result(ranked.get(3))).containsExactly(91, 89, "B+");
    }

    @Test
    void 등급제_과목에_등급을_넣으면_400() throws Exception {
        save(body(BLANK, "\"midScr\":90,\"finScr\":90,\"hwScr\":90,\"attScr\":90,\"ltrGrd\":\"P\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
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

    // ── 상대평가: 구역별 상한 ──

    @Test
    void 상대평가는_석차로_구역을_나눠_상한만_두고_점수를_올리지_않는다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));

        // [원점수 총점, 최종점수, 등급]. A 구역(1~4위)은 그대로
        assertThat(result(ranked.get(0))).containsExactly(98, 98, "A+");
        assertThat(result(ranked.get(1))).containsExactly(95, 95, "A0");
        assertThat(result(ranked.get(3))).containsExactly(88, 88, "B+"); // A 구역이어도 90 으로 올리지 않음
        // B 구역(5~9위)은 89 상한, 낮은 점수는 그대로
        assertThat(result(ranked.get(4))).containsExactly(87, 87, "B+");
        assertThat(result(ranked.get(7))).containsExactly(79, 79, "C+"); // 80 으로 올리지 않음
        // 나머지는 79 상한
        assertThat(result(ranked.get(9))).containsExactly(77, 77, "C+");
        assertThat(result(ranked.get(11))).containsExactly(58, 58, "F");
        sheet(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(student(ranked.get(3), "totScr").value(contains(88)))
                .andExpect(student(ranked.get(3), "fnlScr").value(contains(88)))
                .andExpect(student(ranked.get(3), "ltrGrd").value(contains("B+")))
                .andExpect(student(ranked.get(3), "grdPt").value(contains(3.5)));
    }

    @Test
    void A구역_밖의_높은_점수는_B구역_상한_89로_내린다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {99, 98, 97, 96, 95, 94, 85, 84, 83, 70, 65, 60});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(3))).containsExactly(96, 96, "A0");
        assertThat(result(ranked.get(4))).containsExactly(95, 89, "B+");
        assertThat(result(ranked.get(5))).containsExactly(94, 89, "B+");
        assertThat(result(ranked.get(9))).containsExactly(70, 70, "C-");
    }

    @Test
    void 아무도_90을_넘지_않으면_A는_0명이다() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(0))).containsExactly(85, 85, "B0");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM grade WHERE lec_no = ? AND ltr_grd LIKE 'A%'",
                        Integer.class, Lecture.DEMO_GRADE))
                .isZero();
    }

    @Test
    void 교수님_예시_10명() throws Exception {
        // 수강생 12명 중 2명을 취소해 10명(A 구역 4위, B 구역 8위까지)
        List<String> students = enrolledStudents(Lecture.DEMO_GRADE);
        for (String stdNo : students.subList(10, 12)) {
            jdbc.update(
                    "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-09-01 10:00:00'"
                            + " WHERE lec_no = ? AND std_no = ?",
                    Lecture.DEMO_GRADE,
                    stdNo);
        }

        // case1: 원점수가 모두 90 이상이면 A 4명 A+, B 4명 B+(89), 나머지 C+(79)
        assertThat(grades(new int[] {100, 99, 98, 97, 96, 96, 96, 95, 94, 93}))
                .containsExactly("A+", "A+", "A+", "A+", "B+", "B+", "B+", "B+", "C+", "C+");
        // case2·case3 모양은 교수가 원점수를 그렇게 매겼을 때 나온다(D 는 D0)
        assertThat(grades(new int[] {100, 95, 92, 91, 89, 85, 84, 82, 72, 65}))
                .containsExactly("A+", "A0", "A-", "A-", "B+", "B0", "B0", "B-", "C-", "D0");
        assertThat(grades(new int[] {88, 82, 81, 80, 78, 77, 75, 65, 64, 64}))
                .containsExactly("B+", "B-", "B-", "B-", "C+", "C+", "C0", "D0", "D0", "D0");
    }

    @Test
    void 원점수_총점은_소수_그대로_석차에_쓴다() throws Exception {
        // 4·5위 원점수 총점 90.5·90.25 는 버리면 둘 다 90 이지만 석차가 갈려 5위는 B 구역 상한 89
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);
        setRaw(Lecture.DEMO_GRADE, ranked.get(3), new BigDecimal("90.5"));
        setRaw(Lecture.DEMO_GRADE, ranked.get(4), new BigDecimal("90.25"));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(3))).containsExactly(90, 90, "A-");
        assertThat(result(ranked.get(4))).containsExactly(90, 89, "B+");
    }

    @Test
    void 교직_과목은_A구역_50퍼센트_나머지는_89_상한() throws Exception {
        jdbc.update("UPDATE course SET cmpl_type = 'TEACHING' WHERE crs_cd = ?", crsCdOf(Lecture.DEMO_GRADE));
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {99, 98, 97, 96, 95, 94, 93, 92, 70, 65, 60, 55});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(5))).containsExactly(94, 94, "A0"); // 6위까지 A 구역
        assertThat(result(ranked.get(6))).containsExactly(93, 89, "B+");
        assertThat(result(ranked.get(11))).containsExactly(55, 55, "F"); // 79 상한 구역 없음, 기준표 그대로
    }

    @Test
    void 같은_점수가_구역에서_갈려_최종점수가_달라지면_저장하지_않는다() throws Exception {
        // 4·5위가 91: A 구역은 91, B 구역은 89 라 달라짐
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 91, 91, 84, 82, 79, 78, 77, 74, 58});

        save(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.DEMO_GRADE, scores(ranked.get(11), 60, 60, 60, 60))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(3))))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(4))));
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"));

        // 9·10위가 80: B 구역은 80, 나머지는 79
        ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 88, 87, 84, 82, 81, 80, 80, 74, 58});
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE)
                .andExpect(jsonPath("$.code").value("GRADE_TIE_AT_BOUNDARY"))
                .andExpect(jsonPath("$.detail", containsString(ranked.get(8))));
    }

    @Test
    void 경계의_같은_점수라도_최종점수가_같으면_저장된다() throws Exception {
        // 4·5위가 87: A 구역도 B 구역도 87
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, new int[] {98, 95, 92, 87, 87, 84, 82, 79, 78, 77, 74, 58});

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(3))).containsExactly(87, 87, "B+");
        assertThat(result(ranked.get(4))).containsExactly(87, 87, "B+");
    }

    @Test
    void 재수강생은_최종점수_93_등급_A마이너스가_상한() throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);
        jdbc.update(
                "UPDATE enrollment SET retake_yn = 'Y' WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                ranked.get(0));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(0))).containsExactly(98, 93, "A-");
        assertThat(result(ranked.get(1))).containsExactly(95, 95, "A0"); // 다른 학생은 그대로
    }

    // ── 절대평가·P/F ──

    @Test
    void 절대평가는_원점수_총점을_그대로_기준표에_넣고_재수강은_93_상한() throws Exception {
        jdbc.update("UPDATE lecture SET rel_grd_exmp_yn = 'Y' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, TOTALS);
        jdbc.update(
                "UPDATE enrollment SET retake_yn = 'Y' WHERE lec_no = ? AND std_no = ?",
                Lecture.DEMO_GRADE,
                ranked.get(0));

        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());

        assertThat(result(ranked.get(0))).containsExactly(98, 93, "A-");
        assertThat(result(ranked.get(3))).containsExactly(88, 88, "B+"); // 올리지 않음
        assertThat(result(ranked.get(7))).containsExactly(79, 79, "C+");
    }

    @Test
    void PF_과목은_반영_비율_없이_점수_없는_P_F만_입력한다() throws Exception {
        int lecNo = passFailLecture();
        String profNo = professorOf(lecNo);
        String stdNo = enrolledStudents(lecNo).get(0);
        jdbc.update(
                "UPDATE lecture SET mid_wt = NULL, fin_wt = NULL, hw_wt = NULL, att_wt = NULL WHERE lec_no = ?", lecNo);

        save(profNo, Role.PROFESSOR, lecNo, passFail(stdNo, "F"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evalMode").value("PASS_FAIL"))
                .andExpect(student(stdNo, "ltrGrd").value(contains("F")))
                .andExpect(student(stdNo, "entered").value(contains(true)))
                .andExpect(student(stdNo, "grdPt").value(contains(nullValue())));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT mid_scr, tot_scr, fnl_scr, ltr_grd FROM grade WHERE lec_no = ? AND std_no = ?", lecNo, stdNo);
        assertThat(row.get("mid_scr")).isNull();
        assertThat(row.get("tot_scr")).isNull();
        assertThat(row.get("fnl_scr")).isNull();
        assertThat(row.get("ltr_grd")).isEqualTo("F");

        save(profNo, Role.PROFESSOR, lecNo, scores(stdNo, 95, 95, 95, 95))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        save(profNo, Role.PROFESSOR, lecNo, passFail(stdNo, "A+")).andExpect(status().isBadRequest());
    }

    @Test
    void PF_과목도_비어_있는_학생이_있으면_제출할_수_없다() throws Exception {
        int lecNo = passFailLecture();
        String profNo = professorOf(lecNo);
        String stdNo = enrolledStudents(lecNo).get(0);
        jdbc.update("UPDATE grade SET ltr_grd = NULL WHERE lec_no = ? AND std_no = ?", lecNo, stdNo);
        jdbc.update("UPDATE lecture SET grd_stat = 'ENTERING' WHERE lec_no = ?", lecNo);

        submit(profNo, lecNo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_INCOMPLETE"));

        save(profNo, Role.PROFESSOR, lecNo, passFail(stdNo, "P")).andExpect(status().isOk());
        submit(profNo, lecNo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grdStat").value("SUBMITTED"));
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
        setRaw(lecNo, stdNo, BigDecimal.valueOf(score));
    }

    private void setRaw(int lecNo, String stdNo, BigDecimal score) {
        jdbc.update(
                "UPDATE grade SET mid_scr = ?, fin_scr = ?, hw_scr = ?, att_scr = ? WHERE lec_no = ? AND std_no = ?",
                score,
                score,
                score,
                score,
                lecNo,
                stdNo);
    }

    /** 원점수 총점을 주고 제출한 뒤의 등급(석차 순) */
    private List<String> grades(int[] totals) throws Exception {
        List<String> ranked = setTotals(Lecture.DEMO_GRADE, totals);
        jdbc.update("UPDATE lecture SET grd_stat = 'ENTERING' WHERE lec_no = ?", Lecture.DEMO_GRADE);
        submit(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE).andExpect(status().isOk());
        return ranked.stream().map(stdNo -> (String) result(stdNo).get(2)).toList();
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

    private static String passFail(String stdNo, String ltrGrd) {
        return "{\"grades\":[{\"stdNo\":\"%s\",\"ltrGrd\":\"%s\"}]}".formatted(stdNo, ltrGrd);
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
