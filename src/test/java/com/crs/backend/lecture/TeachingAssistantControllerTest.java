package com.crs.backend.lecture;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Student;
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

/** 조교 관리(LEC-04). 추가·삭제와 학적·계정 변경은 테스트가 끝나면 롤백됩니다. */
@AutoConfigureMockMvc
@Transactional
class TeachingAssistantControllerTest extends IntegrationTest {

    /** 시드: 199번 경영학원론(2027-1)과 227번 음악의이해(2027-1, 폐강)의 담당 교수 */
    private static final String TA_LECTURE_PROFESSOR = "100003";
    /** 시드: 183번 데이터리터러시의 수강생이 아닌 재학생(컴퓨터학부 2학년) */
    private static final String FREE_STUDENT = "20250003";
    /** 시드: 183번 데이터리터러시의 수강생 */
    private static final String ENROLLED_STUDENT = Student.DEMO;
    /** 시드: 183번 데이터리터러시의 다른 수강생 */
    private static final String OTHER_ENROLLED_STUDENT = "20230002";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    // ── 목록 ──

    @Test
    void 담당_교수는_조교_목록을_본다() throws Exception {
        professor(TA_LECTURE_PROFESSOR, get(path(Lecture.TA_TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.TA_TARGET))
                .andExpect(jsonPath("$.assistants", hasSize(1)))
                .andExpect(jsonPath("$.assistants[0].stdNo").value(Student.TEACHING_ASSISTANT))
                .andExpect(jsonPath("$.assistants[0].usrNm").value("장수아"))
                .andExpect(jsonPath("$.assistants[0].deptNm").value("경영학부"))
                .andExpect(jsonPath("$.assistants[0].yrLvl").value(3))
                .andExpect(jsonPath("$.assistants[0].email").value("20240005@example.ac.kr"));
    }

    @Test
    void 담당_교수가_아니면_403_조교도_403() throws Exception {
        professor(Account.DEMO_PROFESSOR, get(path(Lecture.TA_TARGET)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, get(path(Lecture.TA_TARGET)))
                .andExpect(status().isForbidden());
        as(Account.ADMIN, Role.ADMIN, get(path(Lecture.TA_TARGET))).andExpect(status().isForbidden());
    }

    @Test
    void 없는_강의는_404() throws Exception {
        professor(TA_LECTURE_PROFESSOR, get(path(99999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    // ── 추가 ──

    @Test
    void 담당_교수가_조교를_추가하면_바뀐_목록을_준다() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(FREE_STUDENT)))
                .andExpect(jsonPath("$.assistants[0].usrNm").value("오시우"))
                .andExpect(jsonPath("$.assistants[0].deptNm").value("컴퓨터학부"));
    }

    @Test
    void 그_강의의_수강생은_조교가_될_수_없다() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, ENROLLED_STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_IS_ENROLLED"));
    }

    @Test
    void 수강을_취소한_학생은_조교가_될_수_있다() throws Exception {
        jdbc.update(
                "UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = '2026-12-21 10:00:00'"
                        + " WHERE std_no = ? AND lec_no = ?",
                OTHER_ENROLLED_STUDENT,
                Lecture.DEMO_GRADE);

        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, OTHER_ENROLLED_STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(OTHER_ENROLLED_STUDENT)));
    }

    @Test
    void 이미_배정된_조교는_409() throws Exception {
        addAs(TA_LECTURE_PROFESSOR, Lecture.TA_TARGET, Student.TEACHING_ASSISTANT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ASSISTANT"));
    }

    @Test
    void 학생이_아니거나_없는_학번은_404() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, "99999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, TA_LECTURE_PROFESSOR)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 졸업생과_비활성_계정은_조교로_지정할_수_없다() throws Exception {
        // 졸업 처리와 계정 비활성화는 다른 모듈 일이라 테스트에서만 직접 바꾼다
        jdbc.update("UPDATE student SET std_stat = 'GRADUATED' WHERE std_no = ?", FREE_STUDENT);
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_ELIGIBLE"));

        jdbc.update("UPDATE student SET std_stat = 'ATTENDING' WHERE std_no = ?", FREE_STUDENT);
        jdbc.update("UPDATE user_account SET acct_stat = 'INACTIVE' WHERE usr_no = ?", FREE_STUDENT);
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_ELIGIBLE"));
    }

    @Test
    void 휴학생은_조교로_지정할_수_있다() throws Exception {
        // 20250002 는 T0 에 휴학 중
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, "20250002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants[*].stdNo", contains("20250002")));
    }

    @Test
    void 폐강_강의에는_조교를_추가할_수_없고_목록은_볼_수_있다() throws Exception {
        addAs(TA_LECTURE_PROFESSOR, Lecture.CLOSED, FREE_STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        professor(TA_LECTURE_PROFESSOR, get(path(Lecture.CLOSED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants", hasSize(0)));
    }

    @Test
    void 조교는_조교를_추가할_수_없다() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, post(path(Lecture.TA_TARGET)), body(FREE_STUDENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학번이_비면_400() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ── 삭제 ──

    @Test
    void 담당_교수가_조교를_삭제하면_바뀐_목록을_주고_다시_지우면_404() throws Exception {
        professor(TA_LECTURE_PROFESSOR, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants", hasSize(0)));
        professor(TA_LECTURE_PROFESSOR, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_FOUND"));
    }

    @Test
    void 조교는_자기를_삭제할_수_없다() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
                .andExpect(status().isForbidden());
    }

    // ── 도우미 ──

    private static String path(int lecNo) {
        return "/api/teaching/lectures/" + lecNo + "/assistants";
    }

    private static String body(String stdNo) {
        return "{\"stdNo\": \"" + stdNo + "\"}";
    }

    private ResultActions addAs(String profNo, int lecNo, String stdNo) throws Exception {
        return as(profNo, Role.PROFESSOR, post(path(lecNo)), body(stdNo));
    }

    private ResultActions professor(String profNo, MockHttpServletRequestBuilder request) throws Exception {
        return as(profNo, Role.PROFESSOR, request);
    }

    private ResultActions as(String usrNo, Role role, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, usrNo, role)));
    }

    private ResultActions as(String usrNo, Role role, MockHttpServletRequestBuilder request, String json)
            throws Exception {
        return as(usrNo, role, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
