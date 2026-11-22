package com.crs.backend.lecture;

import com.crs.backend.common.auth.CurrentUser;

/**
 * 강의 관리 권한 확인(약속). 로그인한 사람이 그 강의의 담당 교수이거나 배정된 조교인지 알려 줍니다. 강의 운영·성적 기능(LEC-02~04, GRD-01)과
 * 다른 모듈은 강의·조교배정 테이블을 직접 읽지 않고 이 인터페이스를 씁니다.
 *
 * <p>구현은 {@link TeachingLectureAccess} 입니다.
 */
public interface LectureAccess {

    /**
     * user 가 lecNo 강의의 담당 교수이거나 배정된 조교면 true. 관리자와 없는 강의는 false 입니다. 배정된 조교라도 졸업생이면 false 입니다
     * (졸업생은 로그인과 성적 조회만 할 수 있음). 강의상태(폐강)·성적상태는 보지 않습니다.
     */
    boolean canManage(CurrentUser user, int lecNo);

    /**
     * canManage 가 false 면 403 FORBIDDEN 을 던집니다. 없는 강의도 403 이므로, 없는 강의를 404 로 알려야 하는 기능은 강의가 있는지 먼저
     * 확인한 뒤 부릅니다.
     */
    void checkManage(CurrentUser user, int lecNo);
}
