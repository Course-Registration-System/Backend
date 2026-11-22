package com.crs.backend.lecture.dto;

import java.math.BigDecimal;
import java.util.List;

/** 강의 검색 결과 한 줄. remaining 은 잔여 인원(정원 − 신청인원), full 은 잔여 0(마감)입니다. */
public record LectureSearchItem(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        BigDecimal crdt,
        String cmplType,
        String evalType,
        Integer rcmdYrLvl,
        String profNm,
        boolean engLec,
        int cap,
        int enrlCnt,
        int remaining,
        boolean full,
        String planFile,
        List<LectureSearchSlot> timeSlots) {}
