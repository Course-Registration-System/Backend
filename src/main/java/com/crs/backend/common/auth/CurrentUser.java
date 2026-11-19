package com.crs.backend.common.auth;

/**
 * 지금 로그인한 사용자. 토큰에서 꺼낸 값이라 DB 를 다시 읽지 않습니다.
 *
 * <p>컨트롤러 메서드의 인자로 받아 씁니다: {@code public Foo list(CurrentUser user)}
 */
public record CurrentUser(String usrNo, Role role) {

    public boolean is(Role other) {
        return role == other;
    }
}
