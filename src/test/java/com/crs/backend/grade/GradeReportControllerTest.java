package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 시드 T0 에는 2026-2(9) 성적이 확정 전이라, 확정 후 경우는 s1_grade_close 처럼 그 학기 강의를 모두 확정하고 확인합니다. */
@AutoConfigureMockMvc
@Transactional
class GradeReportControllerTest extends IntegrationTest {

    private static final String SEM_9 = "$.semesters[?(@.semNo == 9)]";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions grades(String stdNo) throws Exception {
        return mvc.perform(get("/api/me/grades").header("Authorization", Bearer.of(jwtProvider, stdNo, Role.STUDENT)));
    }

    private void confirmSecondSemester() {
        jdbc.update("""
                UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = ?, cnfm_dtm = '2026-12-28 10:00:00'
                 WHERE sem_no = ? AND grd_stat <> 'CONFIRMED'
                """, Account.ADMIN, Semester.Y2026_SECOND);
    }

    @Test
    void 확정되지_않은_학기는_보이지_않는다() throws Exception {
        grades(Student.GPA_EXACTLY_4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesters[*].semNo", not(hasItem(Semester.Y2026_SECOND))));
    }

    @Test
    void 학기_평점은_소수_둘째_자리로_반올림한다() throws Exception {
        confirmSecondSemester();
        grades(Student.GPA_EXACTLY_4).andExpect(jsonPath(SEM_9 + ".gpa").value(4.0));
        grades(Student.GPA_JUST_UNDER_4).andExpect(jsonPath(SEM_9 + ".gpa").value(3.98)); // 3.9833
    }

    @Test
    void PF_과목만_들은_학기는_평점이_없다() throws Exception {
        confirmSecondSemester();
        grades(Student.PASS_FAIL_ONLY)
                .andExpect(
                        jsonPath(SEM_9 + ".grades[*].gradePoint", not(hasItem(org.hamcrest.Matchers.notNullValue()))))
                .andExpect(jsonPath(SEM_9 + ".gpa").value((Object) null));
    }

    @Test
    void 재수강한_과목의_이전_성적은_남기되_누적_계산에서_뺀다() throws Exception {
        confirmSecondSemester();
        String body = grades(Student.RETAKE).andReturn().getResponse().getContentAsString();

        List<String> grades = JsonPath.read(body, "$.semesters[*].grades[?(@.crsNm == '이산수학')].ltrGrd");
        List<Boolean> counted = JsonPath.read(body, "$.semesters[*].grades[?(@.crsNm == '이산수학')].counted");
        List<Boolean> retake = JsonPath.read(body, "$.semesters[*].grades[?(@.crsNm == '이산수학')].retake");
        assertThat(grades).containsExactly("F", "A-");
        assertThat(counted).containsExactly(false, true);
        assertThat(retake).containsExactly(false, true);

        // 누적 평점·이수 학점을 응답의 성적표로 다시 계산해도 같다(이전 F 제외, P/F 제외, 채플 제외)
        List<Number> crdt =
                JsonPath.read(body, "$.semesters[*].grades[?(@.counted == true && @.gradePoint != null)].crdt");
        List<Number> point =
                JsonPath.read(body, "$.semesters[*].grades[?(@.counted == true && @.gradePoint != null)].gradePoint");
        double credits = 0;
        double points = 0;
        for (int i = 0; i < crdt.size(); i++) {
            credits += crdt.get(i).doubleValue();
            points += crdt.get(i).doubleValue() * point.get(i).doubleValue();
        }
        Number gpa = JsonPath.read(body, "$.cumulativeGpa");
        assertThat(gpa.doubleValue()).isCloseTo(points / credits, org.assertj.core.data.Offset.offset(0.005));

        List<Number> earned = JsonPath.read(
                body, "$.semesters[*].grades[?(@.counted == true && @.ltrGrd != 'F' && @.cmplType != 'CHAPEL')].crdt");
        Number earnedCredits = JsonPath.read(body, "$.earnedCredits");
        assertThat(earnedCredits.doubleValue())
                .isEqualTo(earned.stream().mapToDouble(Number::doubleValue).sum());
    }

    @Test
    void 채플은_학기마다_따로_이수하고_평점과_이수_학점에서_빠진다() throws Exception {
        String body = grades(Student.GRADUATING).andReturn().getResponse().getContentAsString();
        List<Boolean> counted = JsonPath.read(body, "$.semesters[*].grades[?(@.cmplType == 'CHAPEL')].counted");
        List<Object> points = JsonPath.read(body, "$.semesters[*].grades[?(@.cmplType == 'CHAPEL')].gradePoint");
        assertThat(counted).isNotEmpty().containsOnly(true);
        assertThat(points).containsOnlyNulls();
    }

    @Test
    void 학기는_시간_순서다() throws Exception {
        String body = grades(Student.GRADUATING).andReturn().getResponse().getContentAsString();
        List<Integer> years = JsonPath.read(body, "$.semesters[*].yr");
        assertThat(years).isSorted();
    }

    @Test
    void 학생이_아니면_거부한다() throws Exception {
        mvc.perform(get("/api/me/grades").header("Authorization", Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }
}
