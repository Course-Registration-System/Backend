package com.crs.backend.lecture.dto;

import java.time.LocalTime;

/**
 * 강의시간 한 칸. place 는 화면에 그대로 쓰는 장소 표시입니다: 강의실명, "온라인", "미배정". 강의 검색(REG-01)의 강의시간과 같은 모양입니다.
 */
public record TeachingTimeSlot(
        String dow, LocalTime startTm, LocalTime endTm, String clsType, String roomCd, String place) {}
