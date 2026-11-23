package com.crs.backend.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 데이터로 본 최대학점 산정. s1 오버레이는 시드 생성기가 같은 규칙으로 계산한 진급·졸업·최대학점 결과이므로, s1 상태에서 다시 실행하면
 * 아무것도 바뀌지 않아야 합니다.
 */
@AutoConfigureMockMvc
@Transactional
class MaxCreditSeedTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    String admin;

    @BeforeEach
    void setUp() {
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @Test
    void 직전_정규학기_성적이_확정되지_않았으면_거부하고_아무것도_바꾸지_않는다() throws Exception {
        List<Map<String, Object>> before = students();

        calculate(Semester.Y2027_FIRST)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADES_NOT_CONFIRMED"));

        assertThat(students()).isEqualTo(before);
        assertThat(maxCredits(Semester.Y2027_FIRST)).isEmpty();
    }

    @Test
    void s1_상태에서_다시_실행하면_시드와_결과가_같다() throws Exception {
        SeedTime.apply(dataSource, SeedTime.S1);
        List<Map<String, Object>> studentsBefore = students();
        List<Map<String, Object>> creditsBefore = maxCredits(Semester.Y2027_FIRST);

        calculate(Semester.Y2027_FIRST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regular").value(true))
                .andExpect(jsonPath("$.graduated").value(0))
                .andExpect(jsonPath("$.counts.21").value(3))
                .andExpect(jsonPath("$.counts.18").value(12));

        assertThat(students()).isEqualTo(studentsBefore);
        assertThat(maxCredits(Semester.Y2027_FIRST)).isEqualTo(creditsBefore);
    }

    @Test
    void 평점과_학적상태에_따라_최대학점이_정해진다() throws Exception {
        SeedTime.apply(dataSource, SeedTime.S1);
        calculate(Semester.Y2027_FIRST).andExpect(status().isOk());

        assertThat(maxCredit(Student.GPA_EXACTLY_4)).isEqualTo(21);
        assertThat(maxCredit(Student.GPA_JUST_UNDER_4)).isEqualTo(18);
        assertThat(maxCredit(Student.PASS_FAIL_ONLY)).isEqualTo(18);
        assertThat(maxCredit(Student.RETURNING)).isEqualTo(21);
        assertThat(maxCredit(Student.DEMO)).isEqualTo(21);
        assertThat(maxCredit(Student.FRESHMAN)).isEqualTo(18);
        assertThat(maxCredit(Student.GRADUATING)).isNull();
        assertThat(maxCredit(Student.ON_LEAVE)).isNull();
    }

    @Test
    void 두_번_실행해도_결과가_같다() throws Exception {
        SeedTime.apply(dataSource, SeedTime.S1);
        calculate(Semester.Y2027_FIRST).andExpect(status().isOk());
        List<Map<String, Object>> students = students();
        List<Map<String, Object>> credits = maxCredits(Semester.Y2027_FIRST);

        calculate(Semester.Y2027_FIRST).andExpect(status().isOk());

        assertThat(students()).isEqualTo(students);
        assertThat(maxCredits(Semester.Y2027_FIRST)).isEqualTo(credits);
    }

    @Test
    void 계절학기는_학적상태만_보고_진급_졸업은_하지_않는다() throws Exception {
        // 시드의 계절학기는 T0에 일정이 시작돼 있어서, 일정 없는 2027 여름학기를 새로 만든다
        jdbc.update("INSERT INTO semester (yr, sem_type) VALUES (2027, 'SUMMER')");
        int summer = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        List<Map<String, Object>> before = students();

        calculate(summer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regular").value(false))
                .andExpect(jsonPath("$.graduated").value(0));

        assertThat(students()).isEqualTo(before);
        assertThat(maxCredit(Student.DEMO, summer)).isEqualTo(6);
        assertThat(maxCredit(Student.ON_LEAVE, summer)).isEqualTo(3);
    }

    @Test
    void 일정이_시작된_학기는_산정하지_않는다() throws Exception {
        // 2026 겨울학기는 T0에 정정 기간이 시작된다
        List<Map<String, Object>> before = students();
        List<Map<String, Object>> creditsBefore = maxCredits(Semester.Y2026_WINTER);

        calculate(Semester.Y2026_WINTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_CREDIT_CLOSED"));

        assertThat(students()).isEqualTo(before);
        assertThat(maxCredits(Semester.Y2026_WINTER)).isEqualTo(creditsBefore);
    }

    @Test
    void 뒤_학기_일정이_시작된_지난_학기는_산정하지_않는다() throws Exception {
        // 일정이 없는 2022-1 을 만들어도, 뒤 학기(2026 겨울 등)의 일정이 이미 시작돼 지난 학기다
        jdbc.update("INSERT INTO semester (yr, sem_type) VALUES (2022, 'FIRST')");
        int past = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        List<Map<String, Object>> before = students();

        calculate(past)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_CREDIT_CLOSED"));

        assertThat(students()).isEqualTo(before);
        assertThat(maxCredits(past)).isEmpty();
    }

    @Test
    void 결과_목록을_조회한다() throws Exception {
        SeedTime.apply(dataSource, SeedTime.S1);
        mvc.perform(get("/api/admin/semesters/" + Semester.Y2027_FIRST + "/max-credits")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[?(@.stdNo == '%s')].maxCrdt", Student.GPA_EXACTLY_4)
                        .value(21));
    }

    @Test
    void 학생은_실행할_수_없다() throws Exception {
        mvc.perform(post("/api/admin/semesters/" + Semester.Y2027_FIRST + "/max-credits")
                        .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    private ResultActions calculate(int semNo) throws Exception {
        return mvc.perform(
                post("/api/admin/semesters/" + semNo + "/max-credits").header(HttpHeaders.AUTHORIZATION, admin));
    }

    private List<Map<String, Object>> students() {
        return jdbc.queryForList("SELECT std_no, yr_lvl, std_stat FROM student ORDER BY std_no");
    }

    private List<Map<String, Object>> maxCredits(int semNo) {
        return jdbc.queryForList(
                "SELECT std_no, max_crdt FROM semester_maximum_credit WHERE sem_no = ? ORDER BY std_no", semNo);
    }

    private Integer maxCredit(String stdNo) {
        return maxCredit(stdNo, Semester.Y2027_FIRST);
    }

    private Integer maxCredit(String stdNo, int semNo) {
        return jdbc
                .queryForList(
                        "SELECT max_crdt FROM semester_maximum_credit WHERE std_no = ? AND sem_no = ?",
                        Integer.class,
                        stdNo,
                        semNo)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
