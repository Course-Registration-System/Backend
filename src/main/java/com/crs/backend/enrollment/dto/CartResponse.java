package com.crs.backend.enrollment.dto;

import java.math.BigDecimal;
import java.util.List;

/** 장바구니 목록과 담은 학점(채플 제외), 수강 가능 학점(최대학점). */
public record CartResponse(int semNo, List<CartItemResponse> items, BigDecimal cartCredits, int maxCredit) {}
