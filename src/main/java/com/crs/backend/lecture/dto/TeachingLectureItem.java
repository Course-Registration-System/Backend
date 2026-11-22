package com.crs.backend.lecture.dto;

import java.util.List;

/** 담당 강의 한 건. enrlCnt 는 신청상태가 '신청'인 수강 인원, lecStat 은 OPEN(개설)·CLOSED(폐강)입니다. */
public record TeachingLectureItem(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        int cap,
        int enrlCnt,
        String lecStat,
        List<TeachingTimeSlot> timeSlots) {}
