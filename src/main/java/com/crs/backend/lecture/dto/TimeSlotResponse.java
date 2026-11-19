package com.crs.backend.lecture.dto;

import com.crs.backend.lecture.ClsType;
import com.crs.backend.lecture.Dow;
import com.crs.backend.lecture.LectureTimeSlot;
import java.time.LocalTime;

/** 강의시간 한 칸. 강의실이 없으면 roomCd·roomNm 이 null 입니다. */
public record TimeSlotResponse(
        Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd, String roomNm) {

    public LectureTimeSlot toSlot() {
        return new LectureTimeSlot(dow, startTm, endTm, clsType, roomCd);
    }
}
