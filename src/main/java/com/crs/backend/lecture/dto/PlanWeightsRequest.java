package com.crs.backend.lecture.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 평가 반영 비율 입력(LEC-03). 네 값 모두 0~100 정수이고 합이 100 이어야 합니다. Integer 로 받으면 JSON 30.5 가 30 으로 조용히 잘리므로
 * BigDecimal 로 받아 소수가 있으면 400 으로 막습니다.
 */
public record PlanWeightsRequest(
        @NotNull @Digits(integer = 3, fraction = 0) @DecimalMin("0") @DecimalMax("100")
        BigDecimal midWt,

        @NotNull @Digits(integer = 3, fraction = 0) @DecimalMin("0") @DecimalMax("100")
        BigDecimal finWt,

        @NotNull @Digits(integer = 3, fraction = 0) @DecimalMin("0") @DecimalMax("100")
        BigDecimal hwWt,

        @NotNull @Digits(integer = 3, fraction = 0) @DecimalMin("0") @DecimalMax("100")
        BigDecimal attWt) {}
