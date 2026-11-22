package com.crs.backend.enrollment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주간 시간표(REG-04). totalCredits 는 채플을 넣은 총 신청 학점, credits 는 최대학점과 비교하는 학점(채플 제외)입니다(D8).
 */
public record TimetableResponse(
        int semNo, List<TimetableLecture> lectures, BigDecimal totalCredits, BigDecimal credits, int maxCredit) {}
