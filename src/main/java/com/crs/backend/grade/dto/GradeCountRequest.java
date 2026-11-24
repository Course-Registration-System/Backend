package com.crs.backend.grade.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** 상대평가 등급 인원 저장(GRD-01). null 은 한도까지입니다. 둘을 함께 바꿉니다. */
public record GradeCountRequest(
        @PositiveOrZero Integer aCnt, @PositiveOrZero Integer abCnt) {}
