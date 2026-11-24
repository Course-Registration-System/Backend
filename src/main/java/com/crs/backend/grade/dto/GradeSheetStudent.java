package com.crs.backend.grade.dto;

import java.math.BigDecimal;

/**
 * 성적표의 학생 한 명(GRD-01). 항목 원점수는 소수 셋째 자리까지(끝자리 0 은 빼고 92.5 처럼) 보냅니다. totScr 는 원점수 총점(네 항목이 다
 * 있을 때), fnlScr 는 제출할 때 계산한 최종점수, ltrGrd 는 등급(P/F 과목은 P·F), grdPt 는 등급제 평점입니다. fnlScr·ltrGrd·grdPt 는
 * 제출 뒤에만 있고 원점수를 다시 저장하면 비워집니다. entered 는 네 항목 원점수가 모두 있는지입니다.
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
