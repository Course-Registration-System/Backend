package com.crs.backend.user;

import static com.crs.backend.support.SeedFixtures.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 본인 비밀번호 변경. 바꾼 비밀번호는 테스트가 끝나면 롤백됩니다. */
@AutoConfigureMockMvc
@Transactional
class PasswordChangeTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    void 현재_비밀번호가_맞으면_바뀐다() throws Exception {
        change(PASSWORD, "changed-pw!").andExpect(status().isNoContent());

        login("changed-pw!").andExpect(status().isOk());
        login(PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 현재_비밀번호가_틀리면_바뀌지_않는다() throws Exception {
        change("wrong-password", "changed-pw!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        login(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 로그인하지_않으면_바꿀_수_없다() throws Exception {
        mvc.perform(patch("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"a\", \"newPassword\": \"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions change(String currentPassword, String newPassword) throws Exception {
        return mvc.perform(patch("/api/auth/password")
                .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                        .formatted(currentPassword, newPassword)));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usrNo\": \"%s\", \"password\": \"%s\"}".formatted(Student.DEMO, password)));
    }
}
