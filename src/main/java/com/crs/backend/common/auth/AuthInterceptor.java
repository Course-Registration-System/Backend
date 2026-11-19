package com.crs.backend.common.auth;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 컨트롤러에 들어가기 전에 토큰을 확인합니다. 확인한 사용자는 요청에 담아 두고 CurrentUserArgumentResolver 가 꺼내 줍니다.
 * /api/admin/** 는 관리자만 통과합니다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String CURRENT_USER = CurrentUser.class.getName();
    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    public AuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        CurrentUser user = jwtProvider.parse(header.substring(BEARER.length()));
        if (request.getRequestURI().startsWith("/api/admin/") && !user.is(Role.ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        request.setAttribute(CURRENT_USER, user);
        return true;
    }
}
