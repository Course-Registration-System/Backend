package com.crs.backend.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import com.crs.backend.support.SeedFixtures.Lecture;
import com.crs.backend.support.SeedFixtures.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 약속 LectureAccess. 다른 모듈이 쓰는 권한 확인 기준을 시드 데이터로 확인합니다. 테스트마다 롤백합니다. */
@Transactional
class LectureAccessTest extends IntegrationTest {

    /** 시드: 성적 입력 시연 강의(183)를 담당하지 않는 교수 */
    private static final String OTHER_PROFESSOR = "100003";

    @Autowired
    LectureAccess lectureAccess;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 졸업한_조교는_배정돼_있어도_관리할_수_없다() {
        CurrentUser assistant = new CurrentUser(Student.TEACHING_ASSISTANT, Role.STUDENT);
        assertThat(lectureAccess.canManage(assistant, Lecture.TA_TARGET)).isTrue();

        // 졸업생은 로그인과 성적 조회만 할 수 있다(USR-02). 졸업 처리는 학사 모듈 일이라 테스트에서만 직접 바꾼다
        jdbc.update("UPDATE student SET std_stat = 'GRADUATED' WHERE std_no = ?", Student.TEACHING_ASSISTANT);

        assertThat(lectureAccess.canManage(assistant, Lecture.TA_TARGET)).isFalse();
        assertThatThrownBy(() -> lectureAccess.checkManage(assistant, Lecture.TA_TARGET))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 휴학생_조교는_관리할_수_있다() {
        // 업무기술서가 막는 것은 졸업생뿐이다
        jdbc.update("UPDATE student SET std_stat = 'ON_LEAVE' WHERE std_no = ?", Student.TEACHING_ASSISTANT);

        assertThat(lectureAccess.canManage(
                        new CurrentUser(Student.TEACHING_ASSISTANT, Role.STUDENT), Lecture.TA_TARGET))
                .isTrue();
    }

    @Test
    void 담당_교수는_관리할_수_있고_다른_교수는_못_한다() {
        assertThat(lectureAccess.canManage(professor(Account.DEMO_PROFESSOR), Lecture.DEMO_GRADE))
                .isTrue();
        assertThat(lectureAccess.canManage(professor(OTHER_PROFESSOR), Lecture.DEMO_GRADE))
                .isFalse();
    }

    @Test
    void 배정된_조교는_그_강의만_관리할_수_있다() {
        CurrentUser assistant = new CurrentUser(Student.TEACHING_ASSISTANT, Role.STUDENT);
        assertThat(lectureAccess.canManage(assistant, Lecture.TA_TARGET)).isTrue();
        assertThat(lectureAccess.canManage(assistant, Lecture.DEMO_GRADE)).isFalse();
        assertThat(lectureAccess.canManage(new CurrentUser(Student.DEMO, Role.STUDENT), Lecture.TA_TARGET))
                .isFalse();
    }

    @Test
    void 관리자와_없는_강의는_false() {
        assertThat(lectureAccess.canManage(new CurrentUser(Account.ADMIN, Role.ADMIN), Lecture.DEMO_GRADE))
                .isFalse();
        assertThat(lectureAccess.canManage(professor(Account.DEMO_PROFESSOR), 99999))
                .isFalse();
        assertThat(lectureAccess.canManage(null, Lecture.DEMO_GRADE)).isFalse();
    }

    @Test
    void 폐강_강의도_담당_교수면_관리할_수_있다() {
        assertThat(lectureAccess.canManage(professor(OTHER_PROFESSOR), Lecture.CLOSED))
                .isTrue();
    }

    @Test
    void checkManage_는_관리할_수_없으면_403() {
        assertThatCode(() -> lectureAccess.checkManage(professor(Account.DEMO_PROFESSOR), Lecture.DEMO_GRADE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> lectureAccess.checkManage(professor(OTHER_PROFESSOR), Lecture.DEMO_GRADE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static CurrentUser professor(String profNo) {
        return new CurrentUser(profNo, Role.PROFESSOR);
    }
}
