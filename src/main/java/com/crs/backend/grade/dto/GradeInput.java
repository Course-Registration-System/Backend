package com.crs.backend.grade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 학생 한 명의 원점수 입력(GRD-01). 교수·조교는 항목 원점수(0~100, 소수 셋째 자리까지)만 넣고 등급은 넣지 않습니다(등급제·P/F 같음).
 * 넣은 값으로 그 학생의 원점수 네 개를 통째로 바꾸며 null 은 비움입니다. 일부만 넣어도 저장됩니다.
 *
 * <p>BigDecimal 로 받아 넷째 자리 이하가 있으면 400 으로 막습니다(잘라서 저장하지 않음).
 */
public record GradeInput(
        @NotBlank String stdNo,

        @Digits(integer = 3, fraction = 3) @DecimalMin("0") @DecimalMax("100")
        BigDecimal midScr,

        @Digits(integer = 3, fraction = 3) @DecimalMin("0") @DecimalMax("100")
        BigDecimal finScr,

        @Digits(integer = 3, fraction = 3) @DecimalMin("0") @DecimalMax("100")
        BigDecimal hwScr,

        @Digits(integer = 3, fraction = 3) @DecimalMin("0") @DecimalMax("100")
        BigDecimal attScr) {}
