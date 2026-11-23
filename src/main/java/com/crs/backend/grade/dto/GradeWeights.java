package com.crs.backend.grade.dto;

/** 평가 반영 비율(LEC-03 에서 등록, 합 100). */
public record GradeWeights(int midWt, int finWt, int hwWt, int attWt) {}
