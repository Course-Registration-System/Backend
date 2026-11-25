package com.crs.backend.grade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * 학생 한 명의 성적 입력(GRD-01). 등급제 과목은 항목 원점수(0~100, 소수 셋째 자리까지)만 넣고 등급은 시스템이 계산합니다. P/F 과목은 점수
 * 없이 ltrGrd 에 P 또는 F 만 넣습니다. 넣은 값으로 그 학생의 값을 통째로 바꾸며 null 은 비움입니다. 일부만 넣어도 저장됩니다.
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

        @Pattern(regexp = "P|F") String ltrGrd) {

    public boolean hasScore() {
        return midScr != null || finScr != null || hwScr != null || attScr != null;
    }
}
