package com.crs.backend.enrollment.dto;

import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.math.BigDecimal;
import java.util.List;

/** 장바구니에 담은 강의 하나. cartCnt 는 이 강의를 장바구니에 담은 학생 수입니다. */
public record CartItemResponse(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        BigDecimal crdt,
        String cmplType,
        String profNm,
        int cap,
        int enrlCnt,
        int cartCnt,
        List<TimeSlotResponse> timeSlots) {}
