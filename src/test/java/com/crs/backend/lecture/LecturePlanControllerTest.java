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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * 강의계획서 관리(LEC-03). 시드 기준 시각 T0(2026-12-21)에 2027-1 강의는 장바구니 시작(2027-01-18 10:00) 전이라 등록할 수 있고,
 * 2026-2 강의는 이미 지나 등록할 수 없습니다. 197번 컴파일러(2027-1, 교수 100002)는 시드에 계획서 경로와 비율 30/30/30/10 이
 * 있지만 실제 파일은 없습니다. 파일은 build/test-plan-files 에 쓰고, DB 는 테스트마다 롤백됩니다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "crs.plan.dir=" + LecturePlanControllerTest.PLAN_DIR)
class LecturePlanControllerTest extends IntegrationTest {

    static final String PLAN_DIR = "build/test-plan-files";

    /** 시드: 2027-1 컴파일러(교수 100002), 수강생 없음 */
    private static final int NEXT_TERM = 197;
    /** 시드: 2027-1 장바구니 시작 */
    private static final String CART_START = "2027-01-18T10:00:00";

    private static final byte[] PDF = "%PDF-1.7\n테스트 계획서\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    @BeforeAll
    static void clearFiles() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of(PLAN_DIR));
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    // ── 조회 ──

    @Test
    void 담당_교수는_계획서와_비율과_마감을_본다() throws Exception {
        asProfessor(get(plan(NEXT_TERM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(NEXT_TERM))
                .andExpect(jsonPath("$.crsCd").value("1010021"))
                .andExpect(jsonPath("$.crsNm").value("컴파일러"))
                .andExpect(jsonPath("$.sectNo").value(1))
                .andExpect(jsonPath("$.evalType").value("GRADE"))
                .andExpect(jsonPath("$.hasPlanFile").value(true))
                .andExpect(jsonPath("$.weights.midWt").value(30))
                .andExpect(jsonPath("$.weights.attWt").value(10))
                .andExpect(jsonPath("$.deadline").value(CART_START))
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
        as("100003", Role.PROFESSOR, get(plan(NEXT_TERM))).andExpect(status().isForbidden());
        asProfessor(get(plan(999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    // ── 계획서 올리기 ──

    @Test
    void PDF를_올리면_경로를_저장하고_누구나_로그인하면_내려받는다() throws Exception {
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPlanFile").value(true));

        assertThat(jdbc.queryForObject("SELECT plan_file FROM lecture WHERE lec_no = ?", String.class, NEXT_TERM))
                .isEqualTo("plans/" + Semester.Y2027_FIRST + "/1010021-1.pdf");
        as(Student.DEMO, Role.STUDENT, get(download(NEXT_TERM)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"1010021-1.pdf\""))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void 새로_올리면_이전_파일을_교체한다() throws Exception {
        byte[] second = "%PDF-1.7\n바뀐 계획서".getBytes(StandardCharsets.UTF_8);
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(PDF)).andExpect(status().isOk());
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(second)).andExpect(status().isOk());

        as(Student.DEMO, Role.STUDENT, get(download(NEXT_TERM)))
                .andExpect(content().bytes(second));
    }

    @Test
    void 배정된_조교도_올린다() throws Exception {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (?, ?)",
                NEXT_TERM,
                Student.ON_LEAVE);

        upload(Student.ON_LEAVE, Role.STUDENT, NEXT_TERM, pdf(PDF)).andExpect(status().isOk());
    }

    @Test
    void PDF가_아니거나_비었거나_파일이_없으면_400() throws Exception {
        MockMultipartFile renamed =
                new MockMultipartFile("file", "plan.pdf", "application/pdf", "그냥 텍스트".getBytes(StandardCharsets.UTF_8));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, renamed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(new byte[0]))
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
        upload(
                        Account.DEMO_PROFESSOR,
                        Role.PROFESSOR,
                        NEXT_TERM,
                        new MockMultipartFile("other", "plan.pdf", "application/pdf", PDF))
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_FILE"));
    }

    @Test
    void 파일이_10MB를_넘으면_413() throws Exception {
        byte[] big = new byte[(int) LecturePlanService.MAX_FILE_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(big))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PLAN_FILE_TOO_LARGE"));
    }

    @Test
    void 장바구니_시작_시각부터는_올릴_수_없다() throws Exception {
        TestClock.at(clock, "2027-01-18T09:59:59");
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(PDF)).andExpect(status().isOk());

        TestClock.at(clock, CART_START);
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_PERIOD_CLOSED"));
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":10}")
                .andExpect(jsonPath("$.code").value("PLAN_PERIOD_CLOSED"));
    }

    @Test
    void 장바구니와_수강신청_일정이_없는_학기는_기한이_없다() throws Exception {
        jdbc.update(
                "DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('CART', 'ENROLL')",
                Semester.Y2027_FIRST);

        asProfessor(get(plan(NEXT_TERM)))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.editable").value(true));
        upload(Account.DEMO_PROFESSOR, Role.PROFESSOR, NEXT_TERM, pdf(PDF)).andExpect(status().isOk());
    }

    @Test
    void 폐강_강의는_409() throws Exception {
        String profNo =
                jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, Lecture.CLOSED);

        upload(profNo, Role.PROFESSOR, Lecture.CLOSED, pdf(PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    // ── 반영 비율 ──

    @Test
    void 비율을_저장한다() throws Exception {
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":40,\"finWt\":40,\"hwWt\":20,\"attWt\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights.midWt").value(40))
                .andExpect(jsonPath("$.weights.finWt").value(40))
                .andExpect(jsonPath("$.weights.hwWt").value(20))
                .andExpect(jsonPath("$.weights.attWt").value(0));
    }

    @Test
    void 합이_100이_아니거나_형식이_틀리면_400() throws Exception {
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":40,\"finWt\":40,\"hwWt\":10,\"attWt\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEIGHT_SUM_INVALID"));
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":40,\"finWt\":40,\"hwWt\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":101,\"finWt\":0,\"hwWt\":0,\"attWt\":-1}")
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        weights(Account.DEMO_PROFESSOR, NEXT_TERM, "{\"midWt\":30.5,\"finWt\":39.5,\"hwWt\":20,\"attWt\":10}")
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
        mvc.perform(get(download(NEXT_TERM))).andExpect(status().isUnauthorized());
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
