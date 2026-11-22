package com.crs.backend.academic;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.FakeSchedulePolicy;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 2027-1(11) 시드 기준: 개설 41개(폐강 227번 제외), 225번 대면 미배정, 196번 반영비율 미입력. */
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeSchedulePolicy schedulePolicy;

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
    }

    private ResultActions dashboard(String query) throws Exception {
        return mvc.perform(get("/api/admin/dashboard" + query)
                .header("Authorization", Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN)));
    }

    @Test
    void 개설_강의_수와_미배정_강의_수는_폐강을_빼고_센다() throws Exception {
        // 미배정은 225번 하나. 온라인 222번은 강의실이 없어도 미배정이 아니다
        dashboard("?semNo=11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(11))
                .andExpect(jsonPath("$.openLectureCount").value(41))
                .andExpect(jsonPath("$.unassignedLectureCount").value(1));
    }

    @Test
    void 미배정_칸을_채우면_미배정_강의에서_빠진다() throws Exception {
        jdbc.update("""
                UPDATE lecture_time_slot SET room_cd = (SELECT room_cd FROM classroom ORDER BY room_cd LIMIT 1)
                 WHERE lec_no = ? AND cls_type = 'OFFLINE' AND room_cd IS NULL
                """, Lecture.NO_ROOM);
        dashboard("?semNo=11").andExpect(jsonPath("$.unassignedLectureCount").value(0));
    }

    @Test
    void 정원_마감_강의는_신청인원이_정원에_다다른_개설_강의다() throws Exception {
        // T0 에는 정원이 찬 강의가 없다. 198번과 폐강 227번을 채우면 198번만 나온다
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no IN (?, ?)", Lecture.ONE_SEAT_LEFT, Lecture.CLOSED);
        dashboard("?semNo=11")
                .andExpect(jsonPath("$.fullLectures.length()").value(1))
                .andExpect(jsonPath("$.fullLectures[0].lecNo").value(Lecture.ONE_SEAT_LEFT));
    }

    @Test
    void 미제출_강의는_계획서가_없거나_등급제인데_반영비율이_없는_강의다() throws Exception {
        // 시드: 196·208·220번이 반영비율 없음. P/F 인 채플 211번은 반영비율이 없어도 제출로 본다
        jdbc.update("UPDATE lecture SET plan_file = NULL WHERE lec_no = ?", Lecture.ONE_SEAT_LEFT);
        dashboard("?semNo=11")
                .andExpect(jsonPath("$.missingPlanLectures.length()").value(4))
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 196)].noWeights")
                        .value(true))
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 196)].profEmail")
                        .isNotEmpty())
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 198)].noPlanFile")
                        .value(true))
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 198)].noWeights")
                        .value(false))
                .andExpect(jsonPath("$.missingPlanLectures[*].lecNo", not(hasItem(211))));
    }

    @Test
    void 학기를_비우면_신청_대상_학기_없으면_현재_학기() throws Exception {
        schedulePolicy.at(Semester.Y2027_FIRST, false);
        dashboard("").andExpect(jsonPath("$.semNo").value(Semester.Y2027_FIRST));
        schedulePolicy.reset();
        dashboard("").andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND));
    }

    @Test
    void 관리자가_아니면_거부하고_없는_학기는_404() throws Exception {
        mvc.perform(get("/api/admin/dashboard?semNo=11")
                        .header("Authorization", Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT)))
                .andExpect(status().isForbidden());
        dashboard("?semNo=999").andExpect(status().isNotFound());
    }
}
