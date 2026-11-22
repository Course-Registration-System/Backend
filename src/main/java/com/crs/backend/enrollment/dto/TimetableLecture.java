package com.crs.backend.enrollment.dto;

import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.math.BigDecimal;
import java.util.List;

/** 시간표의 강의 한 개. 칸 배치는 화면이 강의시간(timeSlots)을 보고 합니다. 온라인 칸은 clsType 이 ONLINE 이고 강의실이 null 입니다. */
public record TimetableLecture(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        BigDecimal crdt,
        String cmplType,
        String profNm,
        List<TimeSlotResponse> timeSlots) {}
