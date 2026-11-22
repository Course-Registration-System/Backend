package com.crs.backend.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 선수과목 하나. minReqGrd 를 빼면 D- 로 저장합니다. F 는 지정할 수 없습니다. */
public record PrerequisiteRequest(
        @NotBlank String prereqCrsCd,

        @Pattern(regexp = "A\\+|A0|A-|B\\+|B0|B-|C\\+|C0|C-|D\\+|D0|D-", message = "A+~D- 중 하나여야 합니다.")
        String minReqGrd) {}
