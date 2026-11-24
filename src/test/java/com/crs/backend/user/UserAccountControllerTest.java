package com.crs.backend.user;

import static com.crs.backend.support.SeedFixtures.PASSWORD;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
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

/** 등록·수정·비활성화는 테스트가 끝나면 롤백됩니다(@Transactional). */
@AutoConfigureMockMvc
@Transactional
class UserAccountControllerTest extends IntegrationTest {

    private static final String NEW_STUDENT = """
            {"usrNo": "20279999", "password": "first-pw!", "usrNm": "새학생", "email": "new@example.ac.kr",
             "role": "STUDENT", "deptNo": 101, "yrLvl": 1}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    String admin;

    @BeforeEach
    void setUp() {
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @Test
    void 역할과_검색어로_목록을_거른다() throws Exception {
        mvc.perform(asAdmin(get("/api/admin/users").param("role", "PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role", everyItem(is("PROFESSOR"))));
        mvc.perform(asAdmin(get("/api/admin/users").param("keyword", Student.TEACHING_ASSISTANT)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usrNo").value(Student.TEACHING_ASSISTANT))
                .andExpect(jsonPath("$[0].deptNm").isNotEmpty());
    }

    @Test
    void 관리자_계정은_목록에_나오지_않는다() throws Exception {
        mvc.perform(asAdmin(get("/api/admin/users").param("keyword", Account.ADMIN)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 학생을_등록하면_그_비밀번호로_로그인된다() throws Exception {
        create(NEW_STUDENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.stdStat").value("ATTENDING"))
                .andExpect(jsonPath("$.acctStat").value("ACTIVE"));

        login("20279999", "first-pw!").andExpect(status().isOk());
    }

    @Test
    void 교수를_등록한다() throws Exception {
        create("""
                        {"usrNo": "109999", "password": "first-pw!", "usrNm": "새교수",
                         "role": "PROFESSOR", "deptNo": 102, "office": "경영관 501호"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.office").value("경영관 501호"))
                .andExpect(jsonPath("$.yrLvl").doesNotExist());
    }

    @Test
    void 대학원생은_10자리_학번으로_등록한다() throws Exception {
        create(NEW_STUDENT.replace("20279999", "2027719999")).andExpect(status().isCreated());
    }

    @Test
    void 사용자번호가_역할별_자리수와_다르면_등록할_수_없다() throws Exception {
        // 학부생 8자리, 대학원생 10자리, 교수 6자리 숫자만(USR-01·02)
        for (String usrNo : new String[] {"2027999", "202799999", "2027999A"}) {
            create(NEW_STUDENT.replace("20279999", usrNo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        create("""
                        {"usrNo": "10999999", "password": "first-pw!", "usrNm": "새교수",
                         "role": "PROFESSOR", "deptNo": 102}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 이미_있는_로그인ID는_등록할_수_없다() throws Exception {
        create(NEW_STUDENT.replace("20279999", Student.DEMO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER_NO"));
    }

    @Test
    void 등록_입력이_잘못되면_막는다() throws Exception {
        create(NEW_STUDENT.replace(", \"yrLvl\": 1", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("학생은 학년이 필요합니다."));
        create(NEW_STUDENT.replace("\"STUDENT\"", "\"ADMIN\"")).andExpect(status().isBadRequest());
        create(NEW_STUDENT.replace("\"yrLvl\": 1", "\"yrLvl\": 5")).andExpect(status().isBadRequest());
        create(NEW_STUDENT.replace("first-pw!", "가".repeat(25))).andExpect(status().isBadRequest());
        create(NEW_STUDENT.replace("101", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void 휴학생을_복학_처리한다() throws Exception {
        mvc.perform(asAdmin(put("/api/admin/users/" + Student.RETURNING))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usrNm": "한유나", "email": "20250002@example.ac.kr", "deptNo": 102,
                                 "yrLvl": 2, "stdStat": "ATTENDING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stdStat").value("ATTENDING"));
    }

    @Test
    void 비밀번호를_초기화하면_새_비밀번호로만_로그인된다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/users/" + Student.DEMO + "/password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"reset-pw!\"}"))
                .andExpect(status().isNoContent());

        login(Student.DEMO, "reset-pw!").andExpect(status().isOk());
        login(Student.DEMO, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_계정_비밀번호와_빈_비밀번호는_초기화할_수_없다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/users/" + Student.DEMO + "/password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(asAdmin(patch("/api/admin/users/" + Account.ADMIN + "/password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"reset-pw!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비활성화하면_로그인할_수_없다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/users/" + Student.DEMO + "/deactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctStat").value("INACTIVE"));

        login(Student.DEMO, PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void 관리자_계정과_없는_계정은_다룰_수_없다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/users/" + Account.ADMIN + "/deactivate")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(asAdmin(patch("/api/admin/users/00000000/deactivate"))).andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        String student = Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private ResultActions login(String usrNo, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usrNo\": \"%s\", \"password\": \"%s\"}".formatted(usrNo, password)));
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(asAdmin(post("/api/admin/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, admin);
    }
}
