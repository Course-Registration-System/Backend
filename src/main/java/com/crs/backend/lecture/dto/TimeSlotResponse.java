package com.crs.backend.lecture.dto;

import com.crs.backend.lecture.ClsType;
import com.crs.backend.lecture.Dow;
import com.crs.backend.lecture.LectureTimeSlot;
import java.time.LocalTime;

/**
 * 강의시간 한 칸. 강의실이 없으면 roomCd·roomNm 이 null 입니다. place 는 화면에 그대로 쓰는 장소 표시(강의실명, "온라인", "미배정")로,
 * 강의 검색과 같습니다.
 */
public record TimeSlotResponse(
        Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd, String roomNm, String place) {

    /** place 는 수업유형과 강의실명으로 정합니다. */
    public TimeSlotResponse(
            Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd, String roomNm) {
        this(dow, startTm, endTm, clsType, roomCd, roomNm, clsType.place(roomNm));
    }

    public LectureTimeSlot toSlot() {
        return new LectureTimeSlot(dow, startTm, endTm, clsType, roomCd);
    }
}
