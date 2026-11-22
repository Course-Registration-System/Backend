package com.crs.backend.grade.dto;

import java.math.BigDecimal;
import java.util.List;

/** 성적 조회(GRD-03). 학기는 시간 순서이고, 누적 평점은 등급제 과목이 없으면 null 입니다. */
public record GradeReportResponse(List<SemesterGrades> semesters, BigDecimal cumulativeGpa, BigDecimal earnedCredits) {}
