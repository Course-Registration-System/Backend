package com.crs.backend.lecture;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.crs.backend.support.SeedLectures;
import com.crs.backend.support.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 기본 시드 시점 T0(2026-12-21). 2027-1 은 아직 수강신청 전이라 강의시간을 바꿀 수 있습니다. */
@AutoConfigureMockMvc
@Transactional
class LectureAdminControllerTest extends IntegrationTest {

    // 컴파일러(ONE_SEAT_LEFT, 1010021-1, 교수 100002, 정보관 301호 화·목 09:00~10:15)의 2분반을 새로 연다
    private static final String NEW_SECTION = """
            {"crsCd": "1010021", "semNo": 11, "profNo": "100002", "sectNo": 2, "cap": 30,
             "engLec": false, "relGrdExmp": false,
             "timeSlots": [{"dow": "SUN", "startTm": "08:00", "endTm": "09:00", "roomCd": "01101"}]}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SettableClock clock;

    String admin;

    @BeforeEach
    void setUp() {
        admin = Bearer.of(jwtProvider, Account.ADMIN, Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset(clock);
    }

    @Test
    void 학기별_강의_목록을_시간과_함께_보여_준다() throws Exception {
        mvc.perform(asAdmin(get("/api/admin/lectures").param("semNo", String.valueOf(Semester.Y2027_FIRST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.lecNo == %d)].lecStat".formatted(Lecture.CLOSED), hasItem("CLOSED")))
                .andExpect(jsonPath(
                        "$[?(@.lecNo == %d)].timeSlots[0].clsType".formatted(Lecture.ONLINE), hasItem("ONLINE")));
        mvc.perform(asAdmin(get("/api/admin/lectures"))).andExpect(status().isBadRequest());
    }

    @Test
    void 강의를_개설한다() throws Exception {
        create(NEW_SECTION)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecStat").value("OPEN"))
                .andExpect(jsonPath("$.enrlCnt").value(0))
                .andExpect(jsonPath("$.timeSlots[0].roomNm").value("대강당"));
    }

    @Test
    void 대면_강의는_강의실_미배정으로_개설할_수_있다() throws Exception {
        create(NEW_SECTION.replace(", \"roomCd\": \"01101\"", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeSlots[0].clsType").value("OFFLINE"))
                .andExpect(jsonPath("$.timeSlots[0].roomCd").doesNotExist());
    }

    @Test
    void 같은_분반은_다시_열_수_없다() throws Exception {
        create(NEW_SECTION.replace("\"sectNo\": 2", "\"sectNo\": 1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SECTION"));
    }

    @Test
    void 강의실이나_교수_시간이_겹치면_막는다() throws Exception {
        // 컴파일러가 정보관 301호(21301) 화 09:00~10:15 를 쓰고, 교수 100002 가 담당한다
        String tuesday = "{\"dow\": \"TUE\", \"startTm\": \"09:30\", \"endTm\": \"10:30\", \"roomCd\": \"%s\"}";
        create(NEW_SECTION
                        .replaceAll("\\{\"dow\".*}]", tuesday.formatted("21301") + "]")
                        .replace("100002", "100001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_TIME_CONFLICT"));
        create(NEW_SECTION.replaceAll("\\{\"dow\".*}]", tuesday.formatted("01101") + "]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFESSOR_TIME_CONFLICT"));
    }

    @Test
    void 강의시간_입력이_잘못되면_막는다() throws Exception {
        create(NEW_SECTION.replace("\"endTm\": \"09:00\"", "\"endTm\": \"07:00\""))
                .andExpect(status().isBadRequest());
        create(NEW_SECTION.replace("\"roomCd\"", "\"clsType\": \"ONLINE\", \"roomCd\""))
                .andExpect(status().isBadRequest());
        create(NEW_SECTION.replace("}]}", "}, {\"dow\": \"SUN\", \"startTm\": \"08:30\", \"endTm\": \"09:30\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("같은 강의의 강의시간끼리 겹칩니다."));
        create(NEW_SECTION.replace(
                        "\"timeSlots\": [{\"dow\": \"SUN\", \"startTm\": \"08:00\", \"endTm\": \"09:00\", \"roomCd\": \"01101\"}]",
                        "\"timeSlots\": []"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_과목_강의실과_퇴직_교수는_막는다() throws Exception {
        create(NEW_SECTION.replace("1010021", "9999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        create(NEW_SECTION.replace("01101", "99999"))
                .andExpect(jsonPath("$.code").value("CLASSROOM_NOT_FOUND"));
        create(NEW_SECTION.replace("100002", Account.PROFESSOR_INACTIVE))
                .andExpect(jsonPath("$.code").value("PROFESSOR_NOT_FOUND"));
    }

    @Test
    void 강의실_미배정_강의에_강의실을_배정한다() throws Exception {
        // 창업과혁신(NO_ROOM): 대면인데 강의실 미배정(월·수 13:30~14:45, 교수 100004)
        mvc.perform(asAdmin(put("/api/admin/lectures/" + Lecture.NO_ROOM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profNo": "100004", "cap": 60, "engLec": false, "relGrdExmp": false,
                                 "timeSlots": [
                                   {"dow": "MON", "startTm": "13:30", "endTm": "14:45", "roomCd": "21302"},
                                   {"dow": "WED", "startTm": "13:30", "endTm": "14:45", "roomCd": "21302"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlots[0].roomCd").value("21302"))
                .andExpect(jsonPath("$.timeSlots[1].roomCd").value("21302"));
    }

    @Test
    void 폐강된_강의는_다시_폐강하거나_수정할_수_없다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/lectures/" + Lecture.CLOSED + "/close")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
    }

    // 아래 두 테스트의 2026-2 는 정정 기간(2026-09-08 종료)이 끝난 학기다

    @Test
    void 정정_기간이_끝나면_강의를_수정할_수_없다() throws Exception {
        mvc.perform(asAdmin(put("/api/admin/lectures/" + Lecture.NOT_ENTERED))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profNo": "100002", "cap": 30, "engLec": false, "relGrdExmp": false,
                                 "timeSlots": [{"dow": "SUN", "startTm": "08:00", "endTm": "09:00"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPDATE_PERIOD_OVER"));
    }

    @Test
    void 정정_기간이_끝난_학기의_강의는_폐강할_수_없다() throws Exception {
        mvc.perform(asAdmin(patch("/api/admin/lectures/" + Lecture.NOT_ENTERED + "/close")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLOSE_PERIOD_OVER"));
    }

    @Test
    void 정정_기간이_시작되면_대면_수업은_강의실을_비울_수_없다() throws Exception {
        // 2026 겨울학기 정정 기간은 T0(2026-12-21 10:00)에 시작했다. 1030006-1 은 금 11:00~12:50 정보관 301호
        int winter = SeedLectures.of(jdbc, "1030006", Semester.Y2026_WINTER, 1);
        String body = """
                {"profNo": "100005", "cap": 30, "engLec": false, "relGrdExmp": false,
                 "timeSlots": [{"dow": "FRI", "startTm": "11:00", "endTm": "12:50"%s}]}
                """;
        mvc.perform(asAdmin(put("/api/admin/lectures/" + winter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_REQUIRED"));
        mvc.perform(asAdmin(put("/api/admin/lectures/" + winter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(", \"roomCd\": \"21301\"")))
                .andExpect(status().isOk());
    }

    @Test
    void 정정_일정이_없는_학기는_마지막_수강신청일이_끝나면_수정과_폐강을_할_수_없다() throws Exception {
        jdbc.update("DELETE FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'", Semester.Y2027_FIRST);
        String unassigned = """
                {"profNo": "100004", "cap": 60, "engLec": false, "relGrdExmp": false,
                 "timeSlots": [{"dow": "MON", "startTm": "13:30", "endTm": "14:45"},
                               {"dow": "WED", "startTm": "13:30", "endTm": "14:45"}]}
                """;

        // 마지막 수강신청일(2/12 17:00 종료) 중에는 미배정 그대로 수정할 수 있다
        TestClock.at(clock, "2027-02-12T12:00:00");
        mvc.perform(asAdmin(put("/api/admin/lectures/" + Lecture.NO_ROOM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unassigned))
                .andExpect(status().isOk());

        TestClock.at(clock, "2027-02-12T17:00:00");
        mvc.perform(asAdmin(put("/api/admin/lectures/" + Lecture.NO_ROOM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unassigned))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPDATE_PERIOD_OVER"));
        mvc.perform(asAdmin(patch("/api/admin/lectures/" + Lecture.NO_ROOM + "/close")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLOSE_PERIOD_OVER"));
    }

    @Test
    void 없는_강의는_404() throws Exception {
        mvc.perform(asAdmin(get("/api/admin/lectures/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(asAdmin(post("/api/admin/lectures"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, admin);
    }
}
