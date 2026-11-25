package com.crs.backend.lecture;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Semester;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 담당 강의 조회(LEC-01). 시드 기준 시각은 T0(2026-12-21)이고 현재 학기는 2026-2 입니다. */
@AutoConfigureMockMvc
@Transactional
class LectureTeachingControllerTest extends IntegrationTest {

    private static final String PATH = "/api/teaching/lectures";

    /** 시드: 2027-1 폐강 강의(227)의 담당 교수 */
    private static final String CLOSED_LECTURE_PROFESSOR = "100003";
    /** 시드: 2027-1 온라인 강의(222)의 담당 교수 */
    private static final String ONLINE_LECTURE_PROFESSOR = "100005";
    /** 시드: 2027-1 강의실 미배정 강의(225)의 담당 교수 */
    private static final String NO_ROOM_LECTURE_PROFESSOR = "100004";
    /** 시드: 데모 교수의 2026-2 강의(1010023, 월·수). 요일 순서 테스트에서 화·목으로 바꿔 쓴다 */
    private static final int TUE_THU_LECTURE = 161;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 교수는_현재_학기의_담당_강의를_과목코드_순으로_본다() throws Exception {
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND))
                .andExpect(jsonPath("$.yr").value(2026))
                .andExpect(jsonPath("$.semType").value("SECOND"))
                .andExpect(jsonPath("$.lectures", hasSize(6)))
                .andExpect(jsonPath(
                        "$.lectures[*].crsCd",
                        contains("1010005", "1010010", "1010011", "1010012", "1010023", "1030018")))
                // 성적 입력 시연 강의: 수강생 12명, 월·수 대강당
                .andExpect(jsonPath("$.lectures[5].lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.lectures[5].crsNm").value("데이터리터러시"))
                .andExpect(jsonPath("$.lectures[5].sectNo").value(1))
                .andExpect(jsonPath("$.lectures[5].cap").value(60))
                .andExpect(jsonPath("$.lectures[5].enrlCnt").value(12))
                .andExpect(jsonPath("$.lectures[5].lecStat").value("OPEN"))
                .andExpect(jsonPath("$.lectures[5].timeSlots", hasSize(2)))
                .andExpect(jsonPath("$.lectures[5].timeSlots[0].dow").value("MON"))
                .andExpect(jsonPath("$.lectures[5].timeSlots[1].dow").value("WED"))
                .andExpect(jsonPath("$.lectures[5].timeSlots[0].clsType").value("OFFLINE"))
                .andExpect(jsonPath("$.lectures[5].timeSlots[0].roomCd").value("01101"))
                .andExpect(jsonPath("$.lectures[5].timeSlots[0].place").value("대강당"));
    }

    @Test
    void 학기를_지정하면_그_학기의_담당_강의를_본다() throws Exception {
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=" + Semester.Y2027_FIRST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2027_FIRST))
                .andExpect(jsonPath("$.semType").value("FIRST"))
                .andExpect(jsonPath("$.lectures", hasSize(7)))
                .andExpect(jsonPath("$.lectures[0].enrlCnt").value(0));
    }

    @Test
    void 폐강_강의도_CLOSED_로_보인다() throws Exception {
        as(CLOSED_LECTURE_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=" + Semester.Y2027_FIRST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures[?(@.lecNo == " + Lecture.CLOSED + ")].lecStat", contains("CLOSED")));
    }

    @Test
    void 온라인은_온라인_강의실이_없으면_미배정으로_표시한다() throws Exception {
        as(ONLINE_LECTURE_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=" + Semester.Y2027_FIRST)
                .andExpect(jsonPath(
                        "$.lectures[?(@.lecNo == " + Lecture.ONLINE + ")].timeSlots[0].place", contains("온라인")))
                .andExpect(jsonPath(
                        "$.lectures[?(@.lecNo == " + Lecture.ONLINE + ")].timeSlots[0].clsType", contains("ONLINE")));
        as(NO_ROOM_LECTURE_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=" + Semester.Y2027_FIRST)
                .andExpect(jsonPath(
                        "$.lectures[?(@.lecNo == " + Lecture.NO_ROOM + ")].timeSlots[0].place", contains("미배정")))
                .andExpect(jsonPath(
                        "$.lectures[?(@.lecNo == " + Lecture.NO_ROOM + ")].timeSlots[0].clsType", contains("OFFLINE")));
    }

    @Test
    void 조교는_배정된_강의만_본다() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].lecNo").value(Lecture.TA_CURRENT));
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, PATH + "?semNo=" + Semester.Y2027_FIRST)
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].lecNo").value(Lecture.TA_TARGET));
    }

    @Test
    void 졸업한_조교는_배정된_강의가_보이지_않는다() throws Exception {
        // 졸업생은 로그인과 성적 조회만 할 수 있다(USR-02). 졸업 처리는 학사 모듈 일이라 테스트에서만 직접 바꾼다(롤백됨)
        jdbc.update("UPDATE student SET std_stat = 'GRADUATED' WHERE std_no = ?", Student.TEACHING_ASSISTANT);

        as(Student.TEACHING_ASSISTANT, Role.STUDENT, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures", hasSize(0)));
    }

    @Test
    void 조교가_아닌_학생은_빈_목록() throws Exception {
        as(Student.DEMO, Role.STUDENT, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semNo").value(Semester.Y2026_SECOND))
                .andExpect(jsonPath("$.lectures", hasSize(0)));
    }

    @Test
    void 없는_학기는_404() throws Exception {
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=9999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEMESTER_NOT_FOUND"));
    }

    @Test
    void 현재_학기가_지정되지_않았으면_404() throws Exception {
        // 현재 학기 지정은 학사 모듈 일이라 테스트에서만 직접 바꾼다(롤백됨)
        jdbc.update("UPDATE semester SET cur_sem_yn = 'N'");

        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEMESTER_NOT_FOUND"));
    }

    @Test
    void 학기번호가_숫자가_아니면_400() throws Exception {
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH + "?semNo=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관리자는_403_토큰이_없으면_401() throws Exception {
        as(Account.ADMIN, Role.ADMIN, PATH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 강의시간은_요일_순서대로() throws Exception {
        // 1010023 강의를 화·목으로 바꾼다(롤백됨). 기본키 순서(글자순)로는 THU 가 TUE 보다 먼저지만 TUE, THU 순으로 준다
        jdbc.update("DELETE FROM lecture_time_slot WHERE lec_no = ?", TUE_THU_LECTURE);
        jdbc.update(
                "INSERT INTO lecture_time_slot (lec_no, dow, start_tm, end_tm, cls_type, room_cd) VALUES"
                        + " (?, 'THU', '10:30', '11:45', 'OFFLINE', '06102'), (?, 'TUE', '10:30', '11:45', 'OFFLINE', '06102')",
                TUE_THU_LECTURE,
                TUE_THU_LECTURE);

        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, PATH)
                .andExpect(jsonPath("$.lectures[4].lecNo").value(TUE_THU_LECTURE))
                .andExpect(jsonPath("$.lectures[4].timeSlots[*].dow", contains("TUE", "THU")))
                .andExpect(jsonPath("$.lectures[*].timeSlots[*].startTm", hasItem("09:00:00")));
    }

    private ResultActions as(String usrNo, Role role, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }
}
