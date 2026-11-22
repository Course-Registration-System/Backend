package com.crs.backend.enrollment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주간 시간표(REG-04). lectures 는 수강신청 내역과 같은 항목이고, 칸 배치는 화면이 강의시간(timeSlots)을 보고 합니다. 온라인 칸은 clsType 이
 * ONLINE 이고 강의실이 null 입니다. totalCredits 는 채플을 넣은 총 신청 학점입니다.
 */
public record TimetableResponse(int semNo, List<EnrollmentResponse> lectures, BigDecimal totalCredits) {}
