package com.crs.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

/** usrNo 는 로그인ID(학생·조교는 학번, 교수는 교번, 관리자는 직번)입니다. */
public record LoginRequest(@NotBlank String usrNo, @NotBlank String password) {}
