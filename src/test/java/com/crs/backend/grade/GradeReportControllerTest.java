package com.crs.backend.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.common.demo.DemoService;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedTime;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 시드 T0 에는 2026-2(9) 성적이 확정 전이라, 확정 후 경우는 성적 마감 오버레이(s1)를 적용하고 확인합니다. */
@AutoConfigureMockMvc
@Transactional
class GradeReportControllerTest extends IntegrationTest {

    private static final String SEM_9 = "$.semesters[?(@.semNo == 9)]";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    DemoService demoService;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions grades(String stdNo) throws Exception {
        return mvc.perform(get("/api/me/grades").header("Authorization", Bearer.of(jwtProvider, stdNo, Role.STUDENT)));
    }

    // 시드의 성적 마감(s1)을 테스트 트랜잭션 안에서 적용한다. 2026-2 강의가 모두 확정되고, 끝나면 롤백된다
    private void confirmSecondSemester() {
        demoService.applyScenario(SeedTime.S1);
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

        // s1 적용 후 시드 성적 42줄(채플 6줄 포함)로 직접 계산한 값. 2024-1 이산수학 F(3학점)는 재수강해서 누적에서 빠진다
        // 누적 평점 274.2 / 103 = 2.662, 이수 학점 103 - 2026-1 F 3학점 = 100(채플 제외)
        // 2024-1 학기 평점은 그 F 를 넣어 35.1 / 16 = 2.19
        Number gpa = JsonPath.read(body, "$.cumulativeGpa");
        Number earned = JsonPath.read(body, "$.earnedCredits");
        List<Number> firstSemesterGpa = JsonPath.read(body, "$.semesters[?(@.yr == 2024 && @.semType == 'FIRST')].gpa");
        assertThat(gpa.doubleValue()).isEqualTo(2.66);
        assertThat(earned.doubleValue()).isEqualTo(100.0);
        assertThat(firstSemesterGpa).extracting(Number::doubleValue).containsExactly(2.19);
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
    void 학기는_같은_해_안에서도_1학기_여름_2학기_겨울_순서다() throws Exception {
        // 시연 학생은 2026-1, 2026 여름, 2026-2, 2026 겨울을 들었다. 2026-2 는 s1, 겨울은 s2 까지 적용해야 확정된다
        demoService.applyScenario(SeedTime.S1);
        demoService.applyScenario(SeedTime.S2);

        String body = grades(Student.DEMO).andReturn().getResponse().getContentAsString();
        List<Integer> semNos = JsonPath.read(body, "$.semesters[*].semNo");
        List<String> semTypes = JsonPath.read(body, "$.semesters[*].semType");
        assertThat(semNos).containsExactly(7, 8, Semester.Y2026_SECOND, Semester.Y2026_WINTER);
        assertThat(semTypes).containsExactly("FIRST", "SUMMER", "SECOND", "WINTER");
    }

    @Test
    void 학기_번호가_커도_시점이_이른_학기는_시간_순서_자리에_나온다() throws Exception {
        // 나중에 넣은 2025 겨울은 sem_no 가 가장 크지만 2026-1 보다 앞이다. T0 에 시연 학생의 확정 학기는 2026-1, 2026 여름
        jdbc.update("INSERT INTO semester (yr, sem_type) VALUES (2025, 'WINTER')");
        int winter2025 = jdbc.queryForObject(
                "SELECT sem_no FROM semester WHERE yr = 2025 AND sem_type = 'WINTER'", Integer.class);
        jdbc.update("INSERT INTO course (crs_cd, dept_no, crs_nm, crdt, cmpl_type, eval_type)"
                + " VALUES ('Z90001', 101, '테스트계절과목', 3.0, 'GEN_ELEC', 'GRADE')");
        jdbc.update(
                "INSERT INTO lecture (crs_cd, sem_no, prof_no, sect_no, cap, grd_stat, cnfm_emp_no, cnfm_dtm)"
                        + " VALUES ('Z90001', ?, ?, 1, 50, 'CONFIRMED', ?, '2026-01-20 10:00:00')",
                winter2025,
                Account.DEMO_PROFESSOR,
                Account.ADMIN);
        int lecNo = jdbc.queryForObject("SELECT lec_no FROM lecture WHERE crs_cd = 'Z90001'", Integer.class);
        jdbc.update("INSERT INTO enrollment (std_no, lec_no) VALUES (?, ?)", Student.DEMO, lecNo);
        jdbc.update("INSERT INTO grade (std_no, lec_no, ltr_grd) VALUES (?, ?, 'A0')", Student.DEMO, lecNo);

        String body = grades(Student.DEMO).andReturn().getResponse().getContentAsString();
        List<Integer> semNos = JsonPath.read(body, "$.semesters[*].semNo");
        assertThat(winter2025).isGreaterThan(Semester.Y2027_FIRST);
        assertThat(semNos).containsExactly(winter2025, 7, 8);
    }

    @Test
    void 학생이_아니면_거부한다() throws Exception {
        mvc.perform(get("/api/me/grades").header("Authorization", Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }
}
