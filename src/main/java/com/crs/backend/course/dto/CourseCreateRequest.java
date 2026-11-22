package com.crs.backend.course.dto;

import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 과목 등록. evalType 을 빼면 등급제(채플이면 P/F), rcmdYrLvl 을 빼면 권장학년 없음, prerequisites 를 빼면 선수과목 없음입니다.
 * 학점의 0.5 단위와 채플 규칙은 CourseService 가 검사합니다.
 */
public record CourseCreateRequest(
        @NotBlank @Pattern(regexp = "[0-9]{7}", message = "숫자 7자리여야 합니다.")
        String crsCd,

        @NotBlank @Size(max = 100) String crsNm,
        @NotNull Integer deptNo,
        @NotNull @DecimalMin("0.5") @DecimalMax("4") BigDecimal crdt,
        @NotNull CmplType cmplType,
        EvalType evalType,
        @Min(1) @Max(4) Integer rcmdYrLvl,
        List<@NotNull @Valid PrerequisiteRequest> prerequisites) {}
