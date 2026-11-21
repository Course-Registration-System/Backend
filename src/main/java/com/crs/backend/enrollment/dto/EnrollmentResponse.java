package com.crs.backend.enrollment.dto;

import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 수강신청 내역 한 건(신청상태 '신청'). */
public record EnrollmentResponse(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        BigDecimal crdt,
        String cmplType,
        String profNm,
        boolean retake,
        LocalDateTime enrlDtm,
        List<TimeSlotResponse> timeSlots) {}
