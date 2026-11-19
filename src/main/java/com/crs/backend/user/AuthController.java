package com.crs.backend.user;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.user.dto.LoginRequest;
import com.crs.backend.user.dto.LoginResponse;
import com.crs.backend.user.dto.MyInfoResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그아웃 API 는 없습니다. 프론트가 저장한 토큰을 지우면 됩니다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 로그인
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // 새로고침 뒤 토큰으로 내 정보를 다시 받기
    @GetMapping("/me")
    public MyInfoResponse me(CurrentUser user) {
        return authService.myInfo(user);
    }
}
