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
 * 과목 수정. 기본 정보를 통째로 바꿉니다. 과목코드는 바꿀 수 없어 경로로만 받고, 선수과목은 따로 지정합니다.
 *
 * <p>evalType 은 등록과 달리 필수입니다. 빼고 보냈을 때 기본값(등급제)으로 바뀌어 P/F 과목이 조용히 등급제가 되는 일을 막기 위해서입니다.
 * rcmdYrLvl 은 "권장학년 없음"도 정상 값이라 필수가 아니며, 빼면 null 로 지워지므로 바꾸지 않을 때도 현재 값을 채워 보내야 합니다.
 */
public record CourseUpdateRequest(
        @NotBlank @Size(max = 100) String crsNm,
        @NotNull Integer deptNo,
        @NotNull @DecimalMin("0.5") @DecimalMax("4") BigDecimal crdt,
        @NotNull CmplType cmplType,
        @NotNull EvalType evalType,
        @Min(1) @Max(4) Integer rcmdYrLvl) {}
