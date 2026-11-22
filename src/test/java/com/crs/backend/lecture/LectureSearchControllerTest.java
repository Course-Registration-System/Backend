package com.crs.backend.lecture;

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
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import com.crs.backend.support.SeedTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 2027-1(11) 시드 기준: 개설 41개(폐강 227번 1개 제외), 컴퓨터학부(101) 12개, 1학년·권장학년 없음 22개, 김정훈 교수 8개. */
@AutoConfigureMockMvc
@Transactional
class LectureSearchControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    DataSource dataSource;

    @Autowired
    FakeSchedulePolicy schedulePolicy;

    @AfterEach
    void tearDown() {
        schedulePolicy.reset();
    }

    private ResultActions search(String query) throws Exception {
        return mvc.perform(get("/api/lectures?" + query)
                .header("Authorization", Bearer.of(jwtProvider, Student.DEMO, Role.STUDENT)));
    }

    @Test
    void 조건이_없으면_그_학기의_개설_강의를_모두_찾고_폐강은_뺀다() throws Exception {
        search("semNo=11&size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2027_FIRST))
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.items.length()").value(41))
                .andExpect(jsonPath("$.items[*].lecNo", not(hasItem(Lecture.CLOSED))));
    }

    @Test
    void 조건은_들어온_것만_적용한다() throws Exception {
        search("semNo=11&deptNo=101").andExpect(jsonPath("$.total").value(12));
        search("semNo=11&profNm=김정훈").andExpect(jsonPath("$.total").value(8));
        search("semNo=11&crsNm=심리학")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[1].lecNo").value(Lecture.TWO_SECTIONS))
                .andExpect(jsonPath("$.items[1].sectNo").value(2));
        search("semNo=11&deptNo=101&cmplType=MAJOR_ELEC&crsNm=컴파일")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].lecNo").value(Lecture.ONE_SEAT_LEFT));
    }

    @Test
    void 학년을_고르면_권장학년이_없는_교양_채플도_나온다() throws Exception {
        search("semNo=11&yrLvl=1&size=100").andExpect(jsonPath("$.total").value(22));
    }

    @Test
    void 과목명의_퍼센트는_글자_그대로_찾는다() throws Exception {
        search("semNo=11&crsNm=%25").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 강의시간이_두_칸이면_둘_다_나오고_장소는_강의실_온라인_미배정으로_표시한다() throws Exception {
        search("semNo=11&crsNm=과학기술과사회")
                .andExpect(jsonPath("$.items[0].lecNo").value(Lecture.ONLINE))
                .andExpect(jsonPath("$.items[0].timeSlots.length()").value(2))
                .andExpect(jsonPath("$.items[0].timeSlots[*].place", hasItem("온라인")));
        search("semNo=11&crsNm=창업과혁신")
                .andExpect(jsonPath("$.items[0].lecNo").value(Lecture.NO_ROOM))
                .andExpect(jsonPath("$.items[0].timeSlots[0].place").value("미배정"));
        search("semNo=11&crsNm=컴파일러")
                .andExpect(jsonPath("$.items[0].timeSlots[0].place", not("미배정")))
                .andExpect(jsonPath("$.items[0].timeSlots[0].roomCd").isNotEmpty());
    }

    @Test
    void 잔여_인원과_마감을_보여_준다() throws Exception {
        SeedTime.atT1(dataSource); // 4·3학년 신청 후
        search("semNo=11&crsNm=컴퓨터구조")
                .andExpect(jsonPath("$.items[0].lecNo").value(Lecture.FULL))
                .andExpect(jsonPath("$.items[0].remaining").value(0))
                .andExpect(jsonPath("$.items[0].full").value(true));
        search("semNo=11&crsNm=컴파일러")
                .andExpect(jsonPath("$.items[0].remaining").value(1))
                .andExpect(jsonPath("$.items[0].full").value(false));
    }

    @Test
    void 학기를_비우면_신청_대상_학기를_찾는다() throws Exception {
        schedulePolicy.at(Semester.Y2027_FIRST, false);
        search("").andExpect(jsonPath("$.semNo").value(Semester.Y2027_FIRST));
    }

    @Test
    void 신청_대상_학기가_없으면_현재_학기를_찾는다() throws Exception {
        search("").andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND));
    }

    @Test
    void 없는_학기는_404() throws Exception {
        search("semNo=999").andExpect(status().isNotFound());
    }

    @Test
    void 페이지를_나눈다() throws Exception {
        search("semNo=11&page=2&size=20")
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 잘못된_조건은_400() throws Exception {
        search("semNo=11&size=0").andExpect(status().isBadRequest());
        search("semNo=11&yrLvl=5").andExpect(status().isBadRequest());
        search("semNo=11&cmplType=TEACHER").andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/lectures")).andExpect(status().isUnauthorized());
    }
}
