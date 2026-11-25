package com.crs.backend.lecture.dto;

/** 평가 반영 비율(중간·기말·과제·출석, 합 100). 성적 입력(GRD-01) 때 총점 계산에 씁니다. */
public record PlanWeights(int midWt, int finWt, int hwWt, int attWt) {}
