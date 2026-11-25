package com.crs.backend.grade.dto;

import java.math.BigDecimal;

/**
 * 성적표의 학생 한 명(GRD-01). 항목 원점수는 소수 셋째 자리까지(끝자리 0 은 빼고 92.5 처럼) 보냅니다. totScr 는 원점수 총점(네 항목이 다
 * 있을 때, 소수점 버림), fnlScr 는 최종점수, ltrGrd 는 등급(P/F 과목은 점수 없이 P·F), grdPt 는 등급제 평점입니다. 등급제 과목의
 * fnlScr·ltrGrd·grdPt 는 저장할 때마다 원점수가 다 있는 학생으로 다시 계산합니다. entered 는 입력됐는지(등급제는 네 항목 원점수, P/F 는
 * P·F)입니다.
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
        Integer fnlScr,
        String ltrGrd,
        Double grdPt,
        boolean entered) {}
