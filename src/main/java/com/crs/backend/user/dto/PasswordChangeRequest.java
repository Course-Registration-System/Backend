package com.crs.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 본인 비밀번호 변경. 현재 비밀번호가 맞아야 바뀝니다. */
public record PasswordChangeRequest(
        @NotBlank String currentPassword, @NotBlank String newPassword) {}
