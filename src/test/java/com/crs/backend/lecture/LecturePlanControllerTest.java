package com.crs.backend.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

/**
 * 강의계획서 관리(LEC-03). 시드 기준 시각 T0(2026-12-21)에 2027-1 강의는 장바구니 시작 전이라 등록할 수 있고, 2026-2 강의는 이미
 * 지나 등록할 수 없습니다. Lecture.PLAN_EDITABLE(2027-1, DEMO_PROFESSOR 담당)은 시드에 계획서 경로와 비율이 있지만 실제 파일은
 * 없습니다. 파일은 build/test-plan-files 에 쓰고, DB 는 테스트마다 롤백됩니다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "crs.plan.dir=" + LecturePlanControllerTest.PLAN_DIR)
class LecturePlanControllerTest extends IntegrationTest {

    static final String PLAN_DIR = "build/test-plan-files";

    /** 응답의 LocalDateTime 형식 */
    private static final DateTimeFormatter JSON_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final byte[] PDF = "%PDF-1.7\n테스트 계획서\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    /** 시드: Lecture.PLAN_EDITABLE 의 과목·분반·비율 */
    Map<String, Object> editable;
    /** 시드: 2027-1 장바구니 시작(계획서 마감) */
    LocalDateTime cartStart;
    /** 시드: Lecture.PLAN_EDITABLE 을 담당하지 않는 교수 */
    String otherProfessor;

    @BeforeAll
    static void clearFiles() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of(PLAN_DIR));
    }

    @BeforeEach
    void setUp() {
        editable = jdbc.queryForMap("""
                SELECT l.crs_cd, c.crs_nm, l.sect_no, l.mid_wt, l.fin_wt, l.hw_wt, l.att_wt
                  FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.lec_no = ?
                """, Lecture.PLAN_EDITABLE);
        cartStart = jdbc.queryForObject(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'CART'",
                LocalDateTime.class,
                Semester.Y2027_FIRST);
        otherProfessor = jdbc.queryForObject(
                "SELECT MIN(prof_no) FROM professor WHERE prof_no <> ?", String.class, Account.DEMO_PROFESSOR);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    // ── 조회 ──

    @Test
    void 담당_교수는_계획서와_비율과_마감을_본다() throws Exception {
        asProfessor(get(plan(Lecture.PLAN_EDITABLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.PLAN_EDITABLE))
                .andExpect(jsonPath("$.crsCd").value(editable.get("crs_cd")))
                .andExpect(jsonPath("$.crsNm").value(editable.get("crs_nm")))
                .andExpect(jsonPath("$.sectNo").value(((Number) editable.get("sect_no")).intValue()))
                .andExpect(jsonPath("$.evalType").value("GRADE"))
                .andExpect(jsonPath("$.hasPlanFile").value(true))
                .andExpect(jsonPath("$.weights.midWt").value(((Number) editable.get("mid_wt")).intValue()))
                .andExpect(jsonPath("$.weights.finWt").value(((Number) editable.get("fin_wt")).intValue()))
                .andExpect(jsonPath("$.weights.hwWt").value(((Number) editable.get("hw_wt")).intValue()))
                .andExpect(jsonPath("$.weights.attWt").value(((Number) editable.get("att_wt")).intValue()))
                .andExpect(jsonPath("$.deadline").value(cartStart.format(JSON_TIME)))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void 장바구니가_시작된_학기의_강의는_editable_이_false() throws Exception {
        asProfessor(get(plan(Lecture.DEMO_GRADE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void 다른_교수는_403_없는_강의는_404() throws Exception {
        as(otherProfessor, Role.PROFESSOR, get(plan(Lecture.PLAN_EDITABLE))).andExpect(status().isForbidden());
        asProfessor(get(plan(999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    // ── 계획서 올리기 ──

    @Test
    void PDF를_올리면_경로를_저장하고_누구나_로그인하면_내려받는다() throws Exception {
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPlanFile").value(true));

        String fileName = editable.get("crs_cd") + "-" + editable.get("sect_no") + ".pdf";
        assertThat(jdbc.queryForObject(
                        "SELECT plan_file FROM lecture WHERE lec_no = ?", String.class, Lecture.PLAN_EDITABLE))
                .isEqualTo("plans/" + Semester.Y2027_FIRST + "/" + fileName);
        as(Student.DEMO, Role.STUDENT, get(download(Lecture.PLAN_EDITABLE)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\""))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void 새로_올리면_이전_파일을_교체한다() throws Exception {
        byte[] second = "%PDF-1.7\n바뀐 계획서".getBytes(StandardCharsets.UTF_8);
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isOk());
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(second))
                .andExpect(status().isOk());

        as(Student.DEMO, Role.STUDENT, get(download(Lecture.PLAN_EDITABLE)))
                .andExpect(content().bytes(second));
    }

    @Test
    void 배정된_조교도_올린다() throws Exception {
        // 조교 배정은 조교 모듈 일이라 테스트에서만 직접 넣는다(롤백됨)
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                Lecture.PLAN_EDITABLE,
                Student.TEACHING_ASSISTANT);

        upload(Student.TEACHING_ASSISTANT, Role.STUDENT, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_교수와_배정되지_않은_학생은_올리기와_비율_변경도_403() throws Exception {
        upload(otherProfessor, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        weights(otherProfessor, Lecture.PLAN_EDITABLE, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":10}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        upload(Student.DEMO, Role.STUDENT, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 거부된 요청은 아무것도 바꾸지 않는다
        asProfessor(get(plan(Lecture.PLAN_EDITABLE)))
                .andExpect(jsonPath("$.weights.midWt").value(((Number) editable.get("mid_wt")).intValue()));
    }

    @Test
    void PDF가_아니거나_비었거나_파일이_없으면_400() throws Exception {
        MockMultipartFile renamed =
                new MockMultipartFile("file", "plan.pdf", "application/pdf", "그냥 텍스트".getBytes(StandardCharsets.UTF_8));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, renamed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(new byte[0]))
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
        upload(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        Lecture.PLAN_EDITABLE,
                        new MockMultipartFile("other", "plan.pdf", "application/pdf", PDF))
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
    }

    @Test
    void 파일이_10MB를_넘으면_413_권한이_없으면_크기보다_403이_먼저() throws Exception {
        byte[] big = new byte[(int) LecturePlanService.MAX_FILE_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(big))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PLAN_FILE_TOO_LARGE"));
        upload(otherProfessor, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(big)).andExpect(status().isForbidden());
    }

    @Test
    void 장바구니_시작_시각부터는_올릴_수_없다() throws Exception {
        TestClock.at(clock, cartStart.minusSeconds(1).format(JSON_TIME));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isOk());

        TestClock.at(clock, cartStart.format(JSON_TIME));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_PERIOD_CLOSED"));
        weights(Account.DEMO_PROFESSOR, Lecture.PLAN_EDITABLE, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":10}")
                .andExpect(jsonPath("$.code").value("PLAN_PERIOD_CLOSED"));
    }

    @Test
    void 장바구니와_수강신청_일정이_없는_학기는_기한이_없다() throws Exception {
        jdbc.update(
                "DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('CART', 'ENROLL')",
                Semester.Y2027_FIRST);

        asProfessor(get(plan(Lecture.PLAN_EDITABLE)))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.editable").value(true));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, Lecture.PLAN_EDITABLE, pdf(PDF))
                .andExpect(status().isOk());
    }

    @Test
    void 폐강_강의는_올리기와_비율_변경_모두_409() throws Exception {
        String profNo =
                jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, Lecture.CLOSED);

        upload(profNo, Role.PROFESSOR, Lecture.CLOSED, pdf(PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        weights(profNo, Lecture.CLOSED, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    // ── 반영 비율 ──

    @Test
    void 비율을_저장한다() throws Exception {
        weights(Account.DEMO_PROFESSOR, Lecture.PLAN_EDITABLE, "{\"midWt\":40,\"finWt\":40,\"hwWt\":20,\"attWt\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights.midWt").value(40))
                .andExpect(jsonPath("$.weights.finWt").value(40))
                .andExpect(jsonPath("$.weights.hwWt").value(20))
                .andExpect(jsonPath("$.weights.attWt").value(0));
    }

    @Test
    void 합이_100이_아니거나_형식이_틀리면_400() throws Exception {
        weights(Account.DEMO_PROFESSOR, Lecture.PLAN_EDITABLE, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEIGHT_SUM_INVALID"));
        weights(Account.DEMO_PROFESSOR, Lecture.PLAN_EDITABLE, "{\"midWt\":40,\"finWt\":40,\"hwWt\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        weights(Account.DEMO_PROFESSOR, Lecture.PLAN_EDITABLE, "{\"midWt\":101,\"finWt\":0,\"hwWt\":0,\"attWt\":-1}")
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        weights(
                        Account.DEMO_PROFESSOR,
                        Lecture.PLAN_EDITABLE,
                        "{\"midWt\":30.5,\"finWt\":39.5,\"hwWt\":20,\"attWt\":10}")
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void PF_과목은_비율을_받지_않는다() throws Exception {
        int lecNo = jdbc.queryForObject("""
                SELECT MIN(l.lec_no) FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND c.eval_type = 'PASS_FAIL' AND l.lec_stat = 'OPEN'
                """, Integer.class, Semester.Y2027_FIRST);
        String profNo = jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, lecNo);

        weights(profNo, lecNo, "{\"midWt\":40,\"finWt\":40,\"hwWt\":20,\"attWt\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEIGHTS_NOT_ALLOWED"));
        as(profNo, Role.PROFESSOR, get(plan(lecNo)))
                .andExpect(jsonPath("$.weights").value(nullValue()));
    }

    // ── 내려받기 ──

    @Test
    void 계획서가_없거나_파일이_없으면_404_로그인하지_않으면_401() throws Exception {
        // 시드에 경로는 있지만 파일은 없다(이 테스트 클래스는 2026-2 강의에 올리지 않는다)
        as(Student.DEMO, Role.STUDENT, get(download(Lecture.DEMO_GRADE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));

        jdbc.update("UPDATE lecture SET plan_file = NULL WHERE lec_no = ?", Lecture.DEMO_GRADE);
        as(Student.DEMO, Role.STUDENT, get(download(Lecture.DEMO_GRADE)))
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));

        as(Student.DEMO, Role.STUDENT, get(download(999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
        mvc.perform(get(download(Lecture.PLAN_EDITABLE))).andExpect(status().isUnauthorized());
    }

    // ── 헬퍼 ──

    private static MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "계획서.pdf", "application/pdf", bytes);
    }

    private ResultActions upload(String usrNo, Role role, int lecNo, MockMultipartFile file) throws Exception {
        return as(usrNo, role, multipart(HttpMethod.PUT, plan(lecNo) + "/file").file(file));
    }

    private ResultActions weights(String profNo, int lecNo, String body) throws Exception {
        return as(
                profNo,
                Role.PROFESSOR,
                put(plan(lecNo) + "/weights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions asProfessor(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return as(Account.DEMO_PROFESSOR, Role.PROFESSOR, request);
    }

    private ResultActions as(String usrNo, Role role, AbstractMockHttpServletRequestBuilder<?> request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private static String plan(int lecNo) {
        return "/api/teaching/lectures/" + lecNo + "/plan";
    }

    private static String download(int lecNo) {
        return "/api/lectures/" + lecNo + "/plan";
    }
}
