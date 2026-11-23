package com.crs.backend.grade.dto;

/**
 * 상대평가 현황. enrolled 는 수강인원, aCount·abCount 는 지금 저장된 등급 기준 인원, 한도는 소수점 아래를 버린 값입니다. 교직 과목은 A등급만
 * 제한하므로 abLimit 이 null 입니다.
 */
public record GradeRatio(int enrolled, int aCount, int aLimit, int abCount, Integer abLimit) {}
