package com.crs.backend.grade.dto;

/**
 * 상대평가 구간 인원(GRD-01). 원점수 총점 순으로 1 ~ aLimit 위는 A구간, aLimit+1 ~ abLimit 위는 B구간, 나머지는 C·D·F 구간입니다.
 * 인원은 소수점 아래를 버립니다. 교직 과목은 A구간만 있어 abLimit 이 null 입니다.
 */
public record GradeBands(int enrolled, int aLimit, Integer abLimit) {}
