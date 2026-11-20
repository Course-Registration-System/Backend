package com.crs.backend.course;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Student;
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

/** 과목 관리(ACA-02). 테스트마다 롤백하므로 시드 데이터는 그대로 남습니다. */
@AutoConfigureMockMvc
@Transactional
class CourseControllerTest extends IntegrationTest {

    /** 시드: 데이터베이스. 선수과목 자료구조(C0), 개설 이력 있음 */
    private static final String DATABASE = "1010014";
    /** 시드: 자료구조. 데이터베이스의 선수과목 */
    private static final String DATA_STRUCTURE = "1010007";
    /** 시드: 데이터베이스응용. 선수과목 데이터베이스 */
    private static final String DATABASE_APPLICATION = "1010017";
    /** 시드: 채플(0.5학점, P/F) */
    private static final String CHAPEL = "1030001";
    /** 시드에 없는 새 과목코드 */
    private static final String NEW_CODE = "1010030";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtProvider.issue(new CurrentUser(Account.ADMIN, Role.ADMIN));
    }

    // ── 권한 ──

    @Test
    void 토큰이_없으면_401_관리자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String studentToken = jwtProvider.issue(new CurrentUser(Student.DEMO, Role.STUDENT));
        mvc.perform(get("/api/admin/courses").header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ── 목록·상세 ──

    @Test
    void 목록은_조건으로_거르고_선수과목과_개설_이력을_함께_준다() throws Exception {
        perform(get("/api/admin/courses?deptNo=101&cmplType=MAJOR_ELEC&keyword=데이터베이스"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].crsCd", hasItem(DATABASE)))
                .andExpect(jsonPath("$[*].deptNo", everyItem(org.hamcrest.Matchers.is(101))))
                .andExpect(jsonPath("$[*].cmplType", everyItem(org.hamcrest.Matchers.is("MAJOR_ELEC"))))
                .andExpect(jsonPath("$[0].crsCd").value(DATABASE))
                .andExpect(jsonPath("$[0].deptNm").value("컴퓨터학부"))
                .andExpect(jsonPath("$[0].prerequisites", hasSize(1)))
                .andExpect(jsonPath("$[0].prerequisites[0].prereqCrsCd").value(DATA_STRUCTURE))
                .andExpect(jsonPath("$[0].prerequisites[0].prereqCrsNm").value("자료구조"))
                .andExpect(jsonPath("$[0].prerequisites[0].minReqGrd").value("C0"));
    }

    @Test
    void 과목코드_일부로도_찾고_없으면_빈_목록() throws Exception {
        perform(get("/api/admin/courses?keyword=103000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].crsCd", hasItem(CHAPEL)));
        perform(get("/api/admin/courses?keyword=없는과목이름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 목록의_이수구분이_코드가_아니면_400() throws Exception {
        perform(get("/api/admin/courses?cmplType=WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 상세_조회() throws Exception {
        perform(get("/api/admin/courses/" + CHAPEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crsNm").value("채플"))
                .andExpect(jsonPath("$.crdt").value(0.5))
                .andExpect(jsonPath("$.cmplType").value("CHAPEL"))
                .andExpect(jsonPath("$.evalType").value("PASS_FAIL"))
                .andExpect(jsonPath("$.rcmdYrLvl").isEmpty())
                .andExpect(jsonPath("$.prerequisites", hasSize(0)));

        perform(get("/api/admin/courses/9999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    // ── 등록 ──

    @Test
    void 등록하면_201_과_저장된_과목을_준다() throws Exception {
        perform(post("/api/admin/courses"), """
                        {"crsCd": "%s", "crsNm": "클라우드컴퓨팅", "deptNo": 101, "crdt": 3,
                         "cmplType": "MAJOR_ELEC", "rcmdYrLvl": 3,
                         "prerequisites": [{"prereqCrsCd": "1010010", "minReqGrd": "C0"}, {"prereqCrsCd": "1010011"}]}
                        """.formatted(NEW_CODE))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/admin/courses/" + NEW_CODE))
                .andExpect(jsonPath("$.crsCd").value(NEW_CODE))
                .andExpect(jsonPath("$.evalType").value("GRADE"))
                .andExpect(jsonPath("$.prerequisites[0].prereqCrsCd").value("1010010"))
                .andExpect(jsonPath("$.prerequisites[0].prereqCrsNm").value("운영체제"))
                .andExpect(jsonPath("$.prerequisites[0].minReqGrd").value("C0"))
                .andExpect(jsonPath("$.prerequisites[1].minReqGrd").value("D-"));

        perform(get("/api/admin/courses/" + NEW_CODE)).andExpect(status().isOk());
    }

    @Test
    void 이미_있는_과목코드는_409() throws Exception {
        perform(post("/api/admin/courses"), newCourse(DATABASE, "3", "MAJOR_ELEC", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_COURSE_CODE"));
    }

    @Test
    void 채플은_평가방식을_빼면_PF_로_등록한다() throws Exception {
        perform(post("/api/admin/courses"), newCourse(NEW_CODE, "0.5", "CHAPEL", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evalType").value("PASS_FAIL"));
    }

    @Test
    void 채플이_05학점_PF_가_아니면_409() throws Exception {
        perform(post("/api/admin/courses"), newCourse(NEW_CODE, "1", "CHAPEL", "PASS_FAIL"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CHAPEL_COURSE"));
        perform(post("/api/admin/courses"), newCourse(NEW_CODE, "0.5", "CHAPEL", "GRADE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CHAPEL_COURSE"));
    }

    @Test
    void 채플이_아니면_05학점_PF_도_된다() throws Exception {
        perform(post("/api/admin/courses"), newCourse(NEW_CODE, "0.5", "FREE_ELEC", "PASS_FAIL"))
                .andExpect(status().isCreated());
    }

    @Test
    void 학점이_범위_밖이거나_05_단위가_아니면_400() throws Exception {
        for (String crdt : new String[] {"0", "4.5", "2.3"}) {
            perform(post("/api/admin/courses"), newCourse(NEW_CODE, crdt, "MAJOR_ELEC", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 필수값_누락이나_잘못된_코드값은_400() throws Exception {
        perform(post("/api/admin/courses"), """
                        {"crsCd": "%s", "deptNo": 101, "crdt": 3, "cmplType": "MAJOR_ELEC"}
                        """.formatted(NEW_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        perform(post("/api/admin/courses"), newCourse(NEW_CODE, "3", "WRONG", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 과목코드가_숫자_7자리가_아니면_400() throws Exception {
        for (String crsCd : new String[] {"101003", "10100300", "A010030", "abc-1"}) {
            perform(post("/api/admin/courses"), newCourse(crsCd, "3", "MAJOR_ELEC", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.detail").value("crsCd: 숫자 7자리여야 합니다."));
        }
    }

    @Test
    void 없는_학과는_404() throws Exception {
        perform(post("/api/admin/courses"), """
                        {"crsCd": "%s", "crsNm": "새과목", "deptNo": 999, "crdt": 3, "cmplType": "MAJOR_ELEC"}
                        """.formatted(NEW_CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void 등록할_때_선수과목이_잘못되면_과목도_저장하지_않는다() throws Exception {
        perform(post("/api/admin/courses"), withPrerequisites(NEW_CODE, "[{\"prereqCrsCd\": \"9999999\"}]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        perform(
                        post("/api/admin/courses"),
                        withPrerequisites(NEW_CODE, "[{\"prereqCrsCd\": \"%s\"}]".formatted(NEW_CODE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_SELF"));
        perform(
                        post("/api/admin/courses"),
                        withPrerequisites(NEW_CODE, "[{\"prereqCrsCd\": \"1010001\", \"minReqGrd\": \"F\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        perform(get("/api/admin/courses/" + NEW_CODE)).andExpect(status().isNotFound());
    }

    // ── 수정 ──

    @Test
    void 과목_기본_정보를_수정한다() throws Exception {
        perform(put("/api/admin/courses/" + DATABASE), """
                        {"crsNm": "데이터베이스시스템", "deptNo": 102, "crdt": 3, "cmplType": "MAJOR_ELEC",
                         "evalType": "GRADE", "rcmdYrLvl": 2}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crsCd").value(DATABASE))
                .andExpect(jsonPath("$.crsNm").value("데이터베이스시스템"))
                .andExpect(jsonPath("$.deptNo").value(102))
                .andExpect(jsonPath("$.rcmdYrLvl").value(2))
                .andExpect(jsonPath("$.prerequisites[0].prereqCrsCd").value(DATA_STRUCTURE));
    }

    @Test
    void 개설된_과목도_학점_이수구분_평가방식을_바꿀_수_있다() throws Exception {
        // 데이터베이스는 지난 학기들에 개설된 과목. 변경을 막지 않는다(관리자 판단)
        perform(put("/api/admin/courses/" + DATABASE), updateBody("4", "MAJOR_REQ", "PASS_FAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crdt").value(4.0))
                .andExpect(jsonPath("$.cmplType").value("MAJOR_REQ"))
                .andExpect(jsonPath("$.evalType").value("PASS_FAIL"));
    }

    @Test
    void 없는_과목_수정은_404() throws Exception {
        perform(put("/api/admin/courses/9999999"), updateBody("3", "MAJOR_ELEC", "GRADE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    // ── 선수과목 지정 ──

    @Test
    void 선수과목을_통째로_바꾼다() throws Exception {
        perform(put("/api/admin/courses/" + DATABASE_APPLICATION + "/prerequisites"), """
                        {"prerequisites": [{"prereqCrsCd": "1010014", "minReqGrd": "C0"}, {"prereqCrsCd": "1010011"}]}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prerequisites", hasSize(2)))
                .andExpect(jsonPath("$.prerequisites[0].prereqCrsCd").value("1010011"))
                .andExpect(jsonPath("$.prerequisites[0].prereqCrsNm").value("웹프로그래밍"))
                .andExpect(jsonPath("$.prerequisites[0].minReqGrd").value("D-"))
                .andExpect(jsonPath("$.prerequisites[1].prereqCrsCd").value("1010014"))
                .andExpect(jsonPath("$.prerequisites[1].minReqGrd").value("C0"));
    }

    @Test
    void 빈_목록이면_선수과목을_모두_해제한다() throws Exception {
        perform(put("/api/admin/courses/" + DATABASE + "/prerequisites"), "{\"prerequisites\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prerequisites", hasSize(0)));
    }

    @Test
    void 두_과목이_서로를_선수과목으로_가지면_409() throws Exception {
        perform(put("/api/admin/courses/" + DATA_STRUCTURE + "/prerequisites"), """
                        {"prerequisites": [{"prereqCrsCd": "%s"}]}
                        """.formatted(DATABASE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_CYCLE"))
                .andExpect(jsonPath("$.detail").value("선수과목이 순환합니다: 1010007 → 1010014 → 1010007"));
    }

    @Test
    void 세_과목_이상의_순환도_409() throws Exception {
        // 데이터베이스응용 → 데이터베이스 → 자료구조 이므로, 자료구조 → 데이터베이스응용 을 넣으면 순환
        perform(put("/api/admin/courses/" + DATA_STRUCTURE + "/prerequisites"), """
                        {"prerequisites": [{"prereqCrsCd": "%s"}]}
                        """.formatted(DATABASE_APPLICATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_CYCLE"))
                .andExpect(jsonPath("$.detail").value("선수과목이 순환합니다: 1010007 → 1010017 → 1010014 → 1010007"));
    }

    @Test
    void 선수과목_목록의_잘못된_값() throws Exception {
        String path = "/api/admin/courses/" + DATABASE + "/prerequisites";
        perform(put(path), "{\"prerequisites\": [{\"prereqCrsCd\": \"%s\"}]}".formatted(DATABASE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_SELF"));
        perform(put(path), "{\"prerequisites\": [{\"prereqCrsCd\": \"1010001\"}, {\"prereqCrsCd\": \"1010001\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        perform(put(path), "{\"prerequisites\": [{\"prereqCrsCd\": \"1010001\", \"minReqGrd\": \"F\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        perform(put(path), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        perform(put(path), "{\"prerequisites\": [{\"prereqCrsCd\": \"9999999\"}]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        perform(put("/api/admin/courses/9999999/prerequisites"), "{\"prerequisites\": []}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    // ── 도우미 ──

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String newCourse(String crsCd, String crdt, String cmplType, String evalType) {
        String eval = evalType == null ? "" : ", \"evalType\": \"%s\"".formatted(evalType);
        return """
                {"crsCd": "%s", "crsNm": "새과목", "deptNo": 101, "crdt": %s, "cmplType": "%s"%s}
                """.formatted(crsCd, crdt, cmplType, eval);
    }

    private static String withPrerequisites(String crsCd, String prerequisites) {
        return """
                {"crsCd": "%s", "crsNm": "새과목", "deptNo": 101, "crdt": 3, "cmplType": "MAJOR_ELEC",
                 "prerequisites": %s}
                """.formatted(crsCd, prerequisites);
    }

    private static String updateBody(String crdt, String cmplType, String evalType) {
        return """
                {"crsNm": "새이름", "deptNo": 101, "crdt": %s, "cmplType": "%s", "evalType": "%s"}
                """.formatted(crdt, cmplType, evalType);
    }
}
