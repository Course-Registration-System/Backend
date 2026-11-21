package com.crs.backend.lecture.dto;

import com.crs.backend.lecture.ClsType;
import com.crs.backend.lecture.Dow;
import com.crs.backend.lecture.LectureTimeSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;

/** 강의시간 한 칸. 시각은 "09:00" 형식, clsType 을 비우면 대면, roomCd 를 비우면 강의실 미배정입니다. */
public record TimeSlotRequest(
        @NotNull Dow dow,
        @NotNull LocalTime startTm,
        @NotNull LocalTime endTm,
        ClsType clsType,
        @Pattern(regexp = "\\d{5}") String roomCd) {

    public LectureTimeSlot toSlot() {
        return new LectureTimeSlot(dow, startTm, endTm, clsType == null ? ClsType.OFFLINE : clsType, roomCd);
    }
}
