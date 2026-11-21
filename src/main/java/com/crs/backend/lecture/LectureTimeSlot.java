package com.crs.backend.lecture;

import java.time.LocalTime;

/** lecture_time_slot 테이블 한 행(강의시간 한 칸). roomCd 는 온라인이거나 미배정이면 null 입니다. */
public record LectureTimeSlot(Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd) {

    /** 같은 요일에 시간 구간이 조금이라도 겹치면 true. 10:15 에 끝나고 10:15 에 시작하는 건 겹치지 않습니다. */
    public boolean overlaps(LectureTimeSlot other) {
        return dow == other.dow && startTm.isBefore(other.endTm) && other.startTm.isBefore(endTm);
    }
}
