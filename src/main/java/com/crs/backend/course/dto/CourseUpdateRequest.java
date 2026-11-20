package com.crs.backend.course.dto;

import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 과목 수정. 기본 정보를 통째로 바꿉니다. 과목코드는 바꿀 수 없어 경로로만 받고, 선수과목은 따로 지정합니다. 빠진 선택 항목은 등록과 같은 기본값이
 * 됩니다.
 */
public record CourseUpdateRequest(
        @NotBlank @Size(max = 100) String crsNm,
        @NotNull Integer deptNo,
        @NotNull @DecimalMin("0.5") @DecimalMax("4") BigDecimal crdt,
        @NotNull CmplType cmplType,
        EvalType evalType,
        @Min(1) @Max(4) Integer rcmdYrLvl) {}
