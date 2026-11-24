package com.crs.backend.grade.dto;

/**
 * 상대평가 등급 인원(GRD-01). 원점수 총점 석차 1 ~ A 인원은 A군, 그다음 ~ A+B 인원은 B군, 나머지는 그 외입니다. 한도는 A ⌊n×40%⌋·A+B
 * ⌊n×80%⌋(교직은 A ⌊n×50%⌋·A+B n)이고, aCnt·abCnt 는 교수·조교가 정한 인원입니다(null 이면 한도까지).
 */
public record GradeBands(int enrolled, int aLimit, int abLimit, Integer aCnt, Integer abCnt) {}
