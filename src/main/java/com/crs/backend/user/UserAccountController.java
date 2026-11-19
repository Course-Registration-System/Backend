package com.crs.backend.user;

import com.crs.backend.common.auth.Role;
import com.crs.backend.user.dto.UserCreateRequest;
import com.crs.backend.user.dto.UserResponse;
import com.crs.backend.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 계정 관리(USR-02). /api/admin/** 라서 관리자만 들어옵니다(AuthInterceptor). */
@RestController
@RequestMapping("/api/admin/users")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /** 예: /api/admin/users?role=STUDENT&keyword=김 */
    @GetMapping
    public List<UserResponse> search(
            @RequestParam(required = false) Role role, @RequestParam(required = false) String keyword) {
        return userAccountService.search(role, keyword);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userAccountService.create(request);
    }

    // 휴학->복학같은 학적 변경 포함한 put
    @PutMapping("/{usrNo}")
    public UserResponse update(@PathVariable String usrNo, @Valid @RequestBody UserUpdateRequest request) {
        return userAccountService.update(usrNo, request);
    }

    // user 비활성화
    @PatchMapping("/{usrNo}/deactivate")
    public UserResponse deactivate(@PathVariable String usrNo) {
        return userAccountService.deactivate(usrNo);
    }
}
