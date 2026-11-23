package com.crs.backend.grade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 학생 한 명의 성적 입력(GRD-01). 넣은 값으로 그 학생의 행 전체를 바꾸며, null 은 비움입니다.
 *
 * <p>등급제 과목: 네 항목 점수(0~100, 소수 셋째 자리까지), 일부만 넣어도 저장됩니다. ltrGrd 는 상대평가 강의에서 등급을 기준표보다 낮출 때만 넣습니다.
 * P/F 과목: 점수 없이 ltrGrd 에 P 또는 F.
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
        BigDecimal attScr,

        String ltrGrd) {}
