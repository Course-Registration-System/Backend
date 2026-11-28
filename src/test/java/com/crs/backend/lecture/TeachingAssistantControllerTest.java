package com.crs.backend.lecture;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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

/** 조교 관리(LEC-04). 추가·삭제와 학적·계정 변경은 테스트가 끝나면 롤백됩니다. */
@AutoConfigureMockMvc
@Transactional
class TeachingAssistantControllerTest extends IntegrationTest {

    /** 테스트마다 넣는 대학원생(학번 10자리, 소프트웨어학부). 어느 강의에도 배정되지 않았다. 시드의 대학원생은 조교로 이미 배정돼 있어 따로 둔다 */
    private static final String FREE_GRADUATE = "2026710002";
    /** 시드: 학부생(학번 8자리). Lecture.DEMO_GRADE 의 수강생이 아니다 */
    private static final String UNDERGRADUATE = Student.PASS_FAIL_ONLY;
    /** 시드: Lecture.DEMO_GRADE 의 수강생(학부생) */
    private static final String ENROLLED_STUDENT = Student.DEMO;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    /** Lecture.TA_TARGET 과 Lecture.CLOSED 의 담당 교수 */
    String taLectureProfessor;

    @BeforeEach
    void setUp() {
        taLectureProfessor =
                jdbc.queryForObject("SELECT prof_no FROM lecture WHERE lec_no = ?", String.class, Lecture.TA_TARGET);
        // 대학원생 계정 등록은 사용자 모듈 일이라 테스트에서만 직접 넣는다(롤백됨)
        jdbc.update(
                "INSERT INTO user_account (usr_no, pwd, usr_nm, email, role, acct_stat)"
                        + " VALUES (?, 'x', '나대원', ?, 'STUDENT', 'ACTIVE')",
                FREE_GRADUATE,
                FREE_GRADUATE + "@example.ac.kr");
        jdbc.update(
                "INSERT INTO student (std_no, dept_no, yr_lvl, std_stat) VALUES (?, 101, 1, 'ATTENDING')",
                FREE_GRADUATE);
    }

    // ── 목록 ──

    @Test
    void 담당_교수는_조교_목록을_본다() throws Exception {
        professor(taLectureProfessor, get(path(Lecture.TA_TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.TA_TARGET))
                .andExpect(jsonPath("$.assistants", hasSize(1)))
                .andExpect(jsonPath("$.assistants[0].stdNo").value(Student.TEACHING_ASSISTANT))
                .andExpect(jsonPath("$.assistants[0].usrNm").value("정대원"))
                .andExpect(jsonPath("$.assistants[0].deptNm").value("소프트웨어학부"))
                .andExpect(jsonPath("$.assistants[0].yrLvl").value(1))
                .andExpect(jsonPath("$.assistants[0].email").value(Student.TEACHING_ASSISTANT + "@example.ac.kr"));
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
        professor(taLectureProfessor, get(path(99999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURE_NOT_FOUND"));
    }

    // ── 추가 ──

    @Test
    void 담당_교수가_대학원생을_조교로_추가하면_바뀐_목록을_준다() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_GRADUATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecNo").value(Lecture.DEMO_GRADE))
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(FREE_GRADUATE)))
                .andExpect(jsonPath("$.assistants[0].usrNm").value("나대원"))
                .andExpect(jsonPath("$.assistants[0].deptNm").value("소프트웨어학부"));
    }

    @Test
    void 한_대학원생은_여러_강의의_조교가_될_수_있다() throws Exception {
        // 시드의 대학원생 조교는 2026-2·2027-1 강의에 이미 배정돼 있다
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, Student.TEACHING_ASSISTANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(Student.TEACHING_ASSISTANT)));
    }

    @Test
    void 학부생은_조교가_될_수_없다() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, UNDERGRADUATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_GRADUATE"));
        // 그 강의의 수강생도 학부생이라 같은 이유로 거절된다
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, ENROLLED_STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_GRADUATE"));
    }

    @Test
    void 이미_배정된_조교는_409() throws Exception {
        addAs(taLectureProfessor, Lecture.TA_TARGET, Student.TEACHING_ASSISTANT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ASSISTANT"));
    }

    @Test
    void 학생이_아니거나_없는_학번은_404() throws Exception {
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, "9999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, taLectureProfessor)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 졸업생과_비활성_계정은_조교로_지정할_수_없다() throws Exception {
        // 졸업 처리와 계정 비활성화는 다른 모듈 일이라 테스트에서만 직접 바꾼다
        jdbc.update("UPDATE student SET std_stat = 'GRADUATED' WHERE std_no = ?", FREE_GRADUATE);
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_GRADUATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_ELIGIBLE"));

        jdbc.update("UPDATE student SET std_stat = 'ATTENDING' WHERE std_no = ?", FREE_GRADUATE);
        jdbc.update("UPDATE user_account SET acct_stat = 'INACTIVE' WHERE usr_no = ?", FREE_GRADUATE);
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_GRADUATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_NOT_ELIGIBLE"));
    }

    @Test
    void 휴학_중인_대학원생은_조교로_지정할_수_있다() throws Exception {
        jdbc.update("UPDATE student SET std_stat = 'ON_LEAVE' WHERE std_no = ?", FREE_GRADUATE);
        addAs(Account.DEMO_PROFESSOR, Lecture.DEMO_GRADE, FREE_GRADUATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(FREE_GRADUATE)));
    }

    @Test
    void 폐강_강의에는_조교를_추가할_수_없고_목록은_볼_수_있다() throws Exception {
        addAs(taLectureProfessor, Lecture.CLOSED, FREE_GRADUATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_CLOSED"));
        professor(taLectureProfessor, get(path(Lecture.CLOSED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants", hasSize(0)));
    }

    @Test
    void 조교는_조교를_추가할_수_없다() throws Exception {
        as(Student.TEACHING_ASSISTANT, Role.STUDENT, post(path(Lecture.TA_TARGET)), body(FREE_GRADUATE))
                .andExpect(status().isForbidden());
    }

    @Test
    void 담당_교수가_아니면_추가와_삭제도_403() throws Exception {
        String assistant = path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT;
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, post(path(Lecture.TA_TARGET)), body(FREE_GRADUATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(Account.ADMIN, Role.ADMIN, post(path(Lecture.TA_TARGET)), body(FREE_GRADUATE))
                .andExpect(status().isForbidden());
        as(Account.DEMO_PROFESSOR, Role.PROFESSOR, delete(assistant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(Account.ADMIN, Role.ADMIN, delete(assistant)).andExpect(status().isForbidden());

        // 거부된 요청은 아무것도 바꾸지 않는다
        professor(taLectureProfessor, get(path(Lecture.TA_TARGET)))
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(Student.TEACHING_ASSISTANT)));
    }

    @Test
    void 성적이_확정된_강의는_조교를_추가_삭제할_수_없고_목록은_볼_수_있다() throws Exception {
        jdbc.update(
                "UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = ?, cnfm_dtm = '2027-06-30 10:00:00'"
                        + " WHERE lec_no = ?",
                Account.ADMIN,
                Lecture.TA_TARGET);

        addAs(taLectureProfessor, Lecture.TA_TARGET, FREE_GRADUATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_LECTURE_FINISHED"));
        professor(taLectureProfessor, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_LECTURE_FINISHED"));
        professor(taLectureProfessor, get(path(Lecture.TA_TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants[*].stdNo", contains(Student.TEACHING_ASSISTANT)));
    }

    @Test
    void 성적_확정_전이면_제출_상태여도_조교를_추가할_수_있다() throws Exception {
        // 확정 바로 앞 단계인 '제출'까지는 성적을 고칠 수 있어 조교가 할 일이 남아 있다
        jdbc.update("UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = ?", Lecture.TA_TARGET);

        addAs(taLectureProfessor, Lecture.TA_TARGET, FREE_GRADUATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.assistants[*].stdNo", containsInAnyOrder(FREE_GRADUATE, Student.TEACHING_ASSISTANT)));
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
        professor(taLectureProfessor, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistants", hasSize(0)));
        professor(taLectureProfessor, delete(path(Lecture.TA_TARGET) + "/" + Student.TEACHING_ASSISTANT))
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
