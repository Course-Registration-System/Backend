package com.crs.backend.lecture.dto;

import com.crs.backend.lecture.ClsType;
import com.crs.backend.lecture.Dow;
import java.time.LocalTime;

/** 강의시간 한 칸. place 는 화면에 그대로 쓰는 장소 표시입니다: 강의실명, "온라인", "미배정". */
public record LectureSearchSlot(
        Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd, String place) {}
