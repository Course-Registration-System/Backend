package com.crs.backend.academic.dto;

import com.crs.backend.academic.SemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 학기 등록·수정. 현재 학기 지정은 따로 합니다(PUT /api/admin/semesters/{semNo}/current). */
public record SemesterRequest(
        @NotNull @Min(2000) @Max(2100) Integer yr, @NotNull SemType semType) {}
