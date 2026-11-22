package com.crs.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 관리자가 정해 주는 새 비밀번호. 학생·교수가 비밀번호를 잊었을 때 씁니다. */
public record PasswordResetRequest(@NotBlank String newPassword) {}
