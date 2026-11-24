package com.crs.backend.grade.dto;

/**
 * 상대평가 구역(GRD-01). 원점수 총점 석차 1 ~ aLimit 위는 A 구역(최종점수 상한 없음), aLimit+1 ~ abLimit 위는 B 구역(89 상한), 나머지는
 * 79 상한입니다. 점수를 올리지 않으므로 A 인원은 0명일 수 있습니다. 인원은 소수점 아래를 버립니다. 교직 과목은 A 구역 밖이 모두 89 상한이라
 * abLimit 이 null 입니다.
 */
public record GradeBands(int enrolled, int aLimit, Integer abLimit) {}
