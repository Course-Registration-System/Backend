package com.crs.backend.user.dto;

import com.crs.backend.user.StdStat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 계정 수정. 로그인ID·역할·비밀번호는 바꾸지 않습니다.
 *
 * <p>학생이면 yrLvl·stdStat 이 필수이고, 교수면 office 만 씁니다. 역할에 맞지 않는 값은 무시합니다.
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = 50) String usrNm,
        @Email @Size(max = 100) String email,
        @NotNull Integer deptNo,
        @Min(1) @Max(4) Integer yrLvl,
        StdStat stdStat,
        @Size(max = 50) String office) {}
