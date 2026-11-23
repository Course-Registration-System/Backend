package com.crs.backend.grade;

/** 강의의 성적상태(lecture.grd_stat). 미입력 → 입력중 → 제출 → 확정 순서로만 바뀝니다(제출 뒤 고치면 입력중으로 돌아감). */
public enum GrdStat {
    /** 미입력 */
    NOT_ENTERED,
    /** 입력중 */
    ENTERING,
    /** 제출 */
    SUBMITTED,
    /** 확정(GRD-02). 이후 바뀌지 않음 */
    CONFIRMED
}
