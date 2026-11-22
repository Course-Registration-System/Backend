package com.crs.backend.grade.dto;

import java.math.BigDecimal;

/**
 * 성적표 한 줄. gradePoint 는 등급제 과목만 있고 P/F 과목은 null 입니다. counted 가 false 면 나중에 재수강해서 누적 평점·이수 학점
 * 계산에서 빠진 이전 성적입니다.
 */
public record GradeRow(
        int lecNo,
        String crsCd,
        String crsNm,
        BigDecimal crdt,
        String cmplType,
        String evalType,
        String ltrGrd,
        BigDecimal gradePoint,
        boolean retake,
        boolean counted) {}
