package com.crs.backend.grade.dto;

import java.math.BigDecimal;

/**
 * 성적표의 학생 한 명. 항목 점수는 소수 셋째 자리까지(끝자리 0 은 빼고 92.5 처럼), 총점은 정수입니다. 점수·총점·등급이 비어 있으면 null 이고, entered 는 입력을 마쳤는지(등급제는 네 항목 모두, P/F 는 P·F)입니다. grdPt 는
 * 등급제 등급의 평점이며 P/F 과목과 미입력은 null 입니다.
 */
public record GradeSheetStudent(
        String stdNo,
        String usrNm,
        boolean retake,
        BigDecimal midScr,
        BigDecimal finScr,
        BigDecimal hwScr,
        BigDecimal attScr,
        Integer totScr,
        String ltrGrd,
        Double grdPt,
        boolean entered) {}
