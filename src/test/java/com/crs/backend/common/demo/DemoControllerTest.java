package com.crs.backend.common.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures;
import com.crs.backend.support.SeedTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Transactional
class DemoControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetClock() throws Exception {
        mvc.perform(put("/api/dev/clock").param("at", SeedTime.T0));
    }

    @Test
    void 시계를_옮기면_앱의_현재_시각이_바뀐다() throws Exception {
        mvc.perform(get("/api/dev/clock")).andExpect(jsonPath("$.now").value("2026-12-21T10:00"));
        mvc.perform(put("/api/dev/clock").param("at", SeedTime.T1)).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(jsonPath("$.time").value("2027-02-11T10:05+09:00"));
    }

    @Test
    void 오버레이를_적용하면_다음_시점의_데이터가_된다() throws Exception {
        mvc.perform(post("/api/dev/scenarios/" + SeedTime.S1)).andExpect(status().isOk());
        mvc.perform(post("/api/dev/scenarios/" + SeedTime.S2)).andExpect(status().isOk());
        Integer carts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_cart WHERE std_no = ?", Integer.class, SeedFixtures.Student.DEMO);
        assertThat(carts).isPositive();
        mvc.perform(post("/api/dev/scenarios/" + SeedTime.S2)).andExpect(status().isConflict());
    }

    @Test
    void 없는_오버레이는_404() throws Exception {
        mvc.perform(post("/api/dev/scenarios/drop_all")).andExpect(status().isNotFound());
    }
}
