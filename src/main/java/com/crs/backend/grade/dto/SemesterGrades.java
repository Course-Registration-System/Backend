package com.crs.backend.grade.dto;

import java.math.BigDecimal;
import java.util.List;

/** 한 학기의 성적표. gpa 는 그 학기 등급제 과목의 평점(소수 셋째 자리에서 반올림해 둘째 자리까지)이고, 등급제 과목이 없으면 null 입니다. */
public record SemesterGrades(int semNo, int yr, String semType, List<GradeRow> grades, BigDecimal gpa) {}
