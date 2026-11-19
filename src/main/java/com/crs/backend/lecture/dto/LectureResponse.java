package com.crs.backend.lecture.dto;

import com.crs.backend.lecture.LecStat;
import java.math.BigDecimal;
import java.util.List;

/** 강의 한 건과 그 강의시간들. */
public record LectureResponse(
        int lecNo,
        String crsCd,
        String crsNm,
        BigDecimal crdt,
        int semNo,
        String profNo,
        String profNm,
        int sectNo,
        int cap,
        int enrlCnt,
        LecStat lecStat,
        boolean engLec,
        boolean relGrdExmp,
        List<TimeSlotResponse> timeSlots) {}
