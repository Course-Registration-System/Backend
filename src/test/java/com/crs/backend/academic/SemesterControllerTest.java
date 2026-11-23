package com.crs.backend.academic;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 기본 시각은 T0(2026-12-21 10:00, 2026 겨울학기 정정 시작 시각)입니다. 등록·수정·삭제는 테스트가 끝나면 롤백됩니다. */
@AutoConfigureMockMvc
@Transactional
class SemesterControllerTest extends IntegrationTest {

    // 시드의 2027-1 장바구니 일정(2027-01-18 시작, T0 기준 시작 전)과 2026 겨울 정정 일정(T0에 시작)
    private static final int NOT_STARTED_SCHEDULE = 65;
    private static final int STARTED_SCHEDULE = 63;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    String admin;
    String student;

    @BeforeEach
    void setUp() {
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
        student = Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT);
    }

    @Test
    void 학기_목록은_한_해_안에서_1학기_여름_2학기_겨울_순서이고_현재_학기를_표시한다() throws Exception {
        mvc.perform(get("/api/semesters").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[6].semType").value("FIRST"))
                .andExpect(jsonPath("$[7].semType").value("SUMMER"))
                .andExpect(jsonPath("$[7].regular").value(false))
                .andExpect(jsonPath("$[8].semNo").value(Semester.Y2026_SECOND))
                .andExpect(jsonPath("$[8].current").value(true))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));
    }

    @Test
    void 학기를_등록하고_현재_학기로_바꾸면_현재_학기는_하나다() throws Exception {
        int semNo = createSemester(2027, "SECOND");

        mvc.perform(asAdmin(put("/api/admin/semesters/" + semNo + "/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true));
        mvc.perform(get("/api/semesters").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.current == true)].semNo").value(semNo));
    }

    @Test
    void 번호가_작은_이전_학기로도_현재_학기를_바꿀_수_있다() throws Exception {
        mvc.perform(asAdmin(put("/api/admin/semesters/" + Semester.Y2027_FIRST + "/current")))
                .andExpect(status().isOk());

        mvc.perform(asAdmin(put("/api/admin/semesters/" + Semester.Y2026_SECOND + "/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true));
        mvc.perform(get("/api/semesters").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.current == true)].semNo").value(Semester.Y2026_SECOND));
    }

    @Test
    void 이미_있는_학기는_등록할_수_없다() throws Exception {
        semester(post("/api/admin/semesters"), 2026, "SECOND")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEMESTER"));
        semester(post("/api/admin/semesters"), 1999, "FIRST").andExpect(status().isBadRequest());
    }

    @Test
    void 일정이_시작된_학기는_수정할_수_없고_시작_전_학기는_수정한다() throws Exception {
        semester(put("/api/admin/semesters/" + Semester.Y2026_SECOND), 2026, "WINTER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMESTER_IN_USE"));
        semester(put("/api/admin/semesters/" + Semester.Y2027_FIRST), 2028, "FIRST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yr").value(2028));
    }

    @Test
    void 학생은_학기를_등록할_수_없다() throws Exception {
        mvc.perform(post("/api/admin/semesters")
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yr\": 2027, \"semType\": \"SECOND\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 일정을_등록하면_시작_순서로_조회된다() throws Exception {
        int semNo = createSemester(2027, "SECOND");
        schedule(semNo, "CART", null, "2027-07-20T10:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isCreated());
        schedule(semNo, "ENROLL", 3, "2027-08-11T10:00:00", "2027-08-11T17:00:00")
                .andExpect(status().isCreated());
        schedule(semNo, "ENROLL", 4, "2027-08-10T10:00:00", "2027-08-10T17:00:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tgtYrLvl").value(4));
        // 다른 학년끼리는 같은 시간이어도 된다
        schedule(semNo, "ENROLL", 2, "2027-08-10T10:00:00", "2027-08-10T17:00:00")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/semesters/" + semNo + "/schedules").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].schdType").value("CART"))
                .andExpect(jsonPath("$[0].tgtYrLvl").doesNotExist())
                .andExpect(jsonPath("$[1].tgtYrLvl").value(4))
                .andExpect(jsonPath("$[2].tgtYrLvl").value(2))
                .andExpect(jsonPath("$[3].tgtYrLvl").value(3));
    }

    @Test
    void 같은_유형_같은_학년의_기간이_겹치면_막고_전_학년_일정은_모든_학년과_비교한다() throws Exception {
        int semNo = createSemester(2027, "SECOND");
        schedule(semNo, "ENROLL", 4, "2027-08-10T10:00:00", "2027-08-10T17:00:00")
                .andExpect(status().isCreated());

        schedule(semNo, "ENROLL", 4, "2027-08-10T16:00:00", "2027-08-11T17:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
        schedule(semNo, "ENROLL", null, "2027-08-10T12:00:00", "2027-08-12T17:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
        // 끝나는 시각에 바로 시작하는 것은 겹침이 아니다
        schedule(semNo, "ENROLL", 4, "2027-08-10T17:00:00", "2027-08-10T18:00:00")
                .andExpect(status().isCreated());
    }

    @Test
    void 장바구니_수강신청_정정_성적입력_순서를_어기면_막는다() throws Exception {
        int semNo = createSemester(2027, "SECOND");
        schedule(semNo, "ENROLL", null, "2027-08-10T10:00:00", "2027-08-13T17:00:00")
                .andExpect(status().isCreated());

        schedule(semNo, "ADJUST", null, "2027-08-13T10:00:00", "2027-09-08T17:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ORDER"));
        schedule(semNo, "CART", 1, "2027-08-01T10:00:00", "2027-08-10T12:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ORDER"));
        schedule(semNo, "ADJUST", null, "2027-09-02T10:00:00", "2027-09-08T17:00:00")
                .andExpect(status().isCreated());
    }

    @Test
    void 계절학기_성적입력이_다음_정규학기_장바구니_시작보다_늦게_끝나면_막는다() throws Exception {
        int summer = createSemester(2027, "SUMMER");
        int second = createSemester(2027, "SECOND");
        schedule(second, "CART", null, "2027-07-20T10:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isCreated());

        schedule(summer, "GRADE", null, "2027-07-15T10:00:00", "2027-07-20T10:01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ORDER"));
        // 장바구니가 시작하는 시각에 끝나는 것은 된다
        schedule(summer, "GRADE", null, "2027-07-15T10:00:00", "2027-07-20T10:00:00")
                .andExpect(status().isCreated());
    }

    @Test
    void 정규학기_장바구니_수강신청이_직전_계절학기_성적입력_종료보다_먼저_시작하면_막는다() throws Exception {
        int summer = createSemester(2027, "SUMMER");
        schedule(summer, "GRADE", null, "2027-07-10T10:00:00", "2027-07-15T17:00:00")
                .andExpect(status().isCreated());
        int second = createSemester(2027, "SECOND");

        schedule(second, "CART", null, "2027-07-15T10:00:00", "2027-07-20T17:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ORDER"));
        schedule(second, "ENROLL", 4, "2027-07-15T10:00:00", "2027-07-15T18:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ORDER"));
        schedule(second, "CART", null, "2027-07-15T17:00:00", "2027-07-20T17:00:00")
                .andExpect(status().isCreated());
    }

    @Test
    void 시작일시가_종료일시보다_빠르지_않으면_막는다() throws Exception {
        int semNo = createSemester(2027, "SECOND");
        schedule(semNo, "CART", null, "2027-07-24T17:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("시작일시는 종료일시보다 빨라야 합니다."));
        schedule(semNo, "CART", 5, "2027-07-20T10:00:00", "2027-07-24T17:00:00").andExpect(status().isBadRequest());
    }

    @Test
    void 시작일시가_지났거나_지금인_일정은_등록할_수_없다() throws Exception {
        int semNo = createSemester(2027, "SECOND");
        schedule(semNo, "CART", null, "2026-12-20T10:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("시작일시가 지난 일정은 등록할 수 없습니다."));
        // T0 와 같은 시각도 이미 시작된 것으로 본다
        schedule(semNo, "CART", null, "2026-12-21T10:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isBadRequest());
        schedule(semNo, "CART", null, "2026-12-21T10:01:00", "2027-07-24T17:00:00")
                .andExpect(status().isCreated());
    }

    @Test
    void 없는_학기에는_일정을_넣을_수_없다() throws Exception {
        schedule(9999, "CART", null, "2027-07-20T10:00:00", "2027-07-24T17:00:00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEMESTER_NOT_FOUND"));
    }

    @Test
    void 시작_전_일정만_삭제한다() throws Exception {
        mvc.perform(asAdmin(delete("/api/admin/schedules/" + NOT_STARTED_SCHEDULE)))
                .andExpect(status().isNoContent());
        mvc.perform(asAdmin(delete("/api/admin/schedules/" + STARTED_SCHEDULE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_STARTED"));
        mvc.perform(asAdmin(delete("/api/admin/schedules/" + NOT_STARTED_SCHEDULE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    private int createSemester(int yr, String semType) throws Exception {
        String body = semester(post("/api/admin/semesters"), yr, semType)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.semNo");
    }

    private ResultActions semester(MockHttpServletRequestBuilder request, int yr, String semType) throws Exception {
        return mvc.perform(asAdmin(request)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"yr\": %d, \"semType\": \"%s\"}".formatted(yr, semType)));
    }

    private ResultActions schedule(int semNo, String schdType, Integer tgtYrLvl, String start, String end)
            throws Exception {
        String target = tgtYrLvl == null ? "null" : tgtYrLvl.toString();
        return mvc.perform(asAdmin(post("/api/admin/semesters/" + semNo + "/schedules"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schdType\": \"%s\", \"tgtYrLvl\": %s, \"startDtm\": \"%s\", \"endDtm\": \"%s\"}"
                        .formatted(schdType, target, start, end)));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, admin);
    }
}
