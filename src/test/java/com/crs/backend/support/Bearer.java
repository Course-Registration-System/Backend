package com.crs.backend.support;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;

/** 테스트에서 로그인 API 를 거치지 않고 Authorization 헤더 값을 만듭니다. */
public final class Bearer {

    private Bearer() {}

    public static String of(JwtProvider jwtProvider, String usrNo, Role role) {
        return "Bearer " + jwtProvider.issue(new CurrentUser(usrNo, role));
    }
}
