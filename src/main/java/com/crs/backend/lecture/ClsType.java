package com.crs.backend.lecture;

/** 수업유형(lecture_time_slot.cls_type). 온라인 칸에는 강의실을 두지 않습니다. */
public enum ClsType {
    OFFLINE,
    ONLINE;

    /**
     * 화면에 쓰는 장소 표시: 강의실명, 강의실이 없으면 "온라인" 또는 "미배정". 강의 검색(REG-01)·시간표(REG-04)·담당 강의(LEC-01) 등 강의시간을
     * 보여 주는 응답이 모두 이 표시를 씁니다.
     */
    public String place(String roomNm) {
        if (roomNm != null) {
            return roomNm;
        }
        return this == ONLINE ? "온라인" : "미배정";
    }
}
