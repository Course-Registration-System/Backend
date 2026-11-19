package com.crs.backend.user;

import static com.crs.backend.support.SeedFixtures.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Student;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 역할별로_로그인하면_토큰과_사용자_정보를_준다() throws Exception {
        login(Account.ADMIN, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
        login(Account.DEMO_PROFESSOR, PASSWORD)
                .andExpect(jsonPath("$.user.role").value("PROFESSOR"));
        login(Student.DEMO, PASSWORD)
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.ta").value(false));
    }

    @Test
    void 조교로_배정된_학생은_ta_가_true() throws Exception {
        login(Student.TEACHING_ASSISTANT, PASSWORD)
                .andExpect(jsonPath("$.user.ta").value(true));
    }

    @Test
    void 비밀번호가_틀려도_계정이_없어도_같은_오류() throws Exception {
        String message = "로그인 ID 또는 비밀번호가 올바르지 않습니다.";
        login(Student.DEMO, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.detail").value(message));
        login("99999999", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void 비활성_계정은_로그인할_수_없다() throws Exception {
        login(Account.PROFESSOR_INACTIVE, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void 빈_값은_입력_오류() throws Exception {
        login("", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 받은_토큰으로_내_정보를_조회한다() throws Exception {
        String body = login(Student.TEACHING_ASSISTANT, PASSWORD)
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usrNo").value(Student.TEACHING_ASSISTANT))
                .andExpect(jsonPath("$.ta").value(true));
    }

    @Test
    void 토큰이_없거나_위조되면_401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions login(String usrNo, String password) throws Exception {
        String json = """
                {"usrNo": "%s", "password": "%s"}
                """.formatted(usrNo, password);
        return mvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
