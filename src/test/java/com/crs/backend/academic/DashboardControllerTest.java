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

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, Semester.Y2027_FIRST);
    }

    @Test
    void 개설_강의_수와_미배정_강의_수는_폐강을_빼고_센다() throws Exception {
        dashboard("?semNo=11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(11))
                .andExpect(jsonPath("$.openLectureCount").value(41))
                .andExpect(jsonPath("$.unassignedLectureCount").value(count("""
                        SELECT COUNT(DISTINCT l.lec_no) FROM lecture l JOIN lecture_time_slot t ON t.lec_no = l.lec_no
                         WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND t.cls_type = 'OFFLINE' AND t.room_cd IS NULL
                        """)));
    }

    @Test
    void 미배정_강의는_대면_칸에_강의실이_없는_강의이고_온라인은_아니다() throws Exception {
        int before = count("""
                SELECT COUNT(DISTINCT l.lec_no) FROM lecture l JOIN lecture_time_slot t ON t.lec_no = l.lec_no
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND t.cls_type = 'OFFLINE' AND t.room_cd IS NULL
                """);
        // 225번 미배정 칸을 채우면 하나 줄어든다
        jdbc.update("""
                UPDATE lecture_time_slot SET room_cd = (SELECT room_cd FROM classroom ORDER BY room_cd LIMIT 1)
                 WHERE lec_no = ? AND cls_type = 'OFFLINE' AND room_cd IS NULL
                """, Lecture.NO_ROOM);
        dashboard("?semNo=11").andExpect(jsonPath("$.unassignedLectureCount").value(before - 1));
    }

    @Test
    void 정원_마감_강의는_신청인원이_정원에_다다른_개설_강의다() throws Exception {
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no = ?", Lecture.ONE_SEAT_LEFT);
        jdbc.update("UPDATE lecture SET enrl_cnt = cap WHERE lec_no = ?", Lecture.CLOSED);
        dashboard("?semNo=11")
                .andExpect(jsonPath("$.fullLectures[*].lecNo", hasItem(Lecture.ONE_SEAT_LEFT)))
                .andExpect(jsonPath("$.fullLectures[*].lecNo", not(hasItem(Lecture.CLOSED))))
                .andExpect(jsonPath("$.fullLectures.length()").value(count("""
                        SELECT COUNT(*) FROM lecture WHERE sem_no = ? AND lec_stat = 'OPEN' AND enrl_cnt >= cap
                        """)));
    }

    @Test
    void 미제출_강의는_계획서가_없거나_등급제인데_반영비율이_없는_강의다() throws Exception {
        int pfLecture = jdbc.queryForObject("""
                SELECT l.lec_no FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND c.eval_type = 'PASS_FAIL' ORDER BY l.lec_no LIMIT 1
                """, Integer.class, Semester.Y2027_FIRST);
        // P/F 과목은 계획서만 있으면 반영비율이 없어도 제출로 본다
        jdbc.update(
                "UPDATE lecture SET plan_file = 'plan.pdf', mid_wt = NULL, fin_wt = NULL, hw_wt = NULL, att_wt = NULL WHERE lec_no = ?",
                pfLecture);

        dashboard("?semNo=11")
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 196)].noWeights")
                        .value(true))
                .andExpect(jsonPath("$.missingPlanLectures[?(@.lecNo == 196)].profEmail")
                        .isNotEmpty())
                .andExpect(jsonPath("$.missingPlanLectures[*].lecNo", not(hasItem(pfLecture))))
                .andExpect(jsonPath("$.missingPlanLectures[*].lecNo", not(hasItem(Lecture.CLOSED))))
                .andExpect(jsonPath("$.missingPlanLectures.length()").value(count("""
                        SELECT COUNT(*) FROM lecture l JOIN course c ON c.crs_cd = l.crs_cd
                         WHERE l.sem_no = ? AND l.lec_stat = 'OPEN'
                           AND (l.plan_file IS NULL OR (c.eval_type = 'GRADE' AND l.mid_wt IS NULL))
                        """)));
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
