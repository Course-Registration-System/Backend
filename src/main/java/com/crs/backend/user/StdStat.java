package com.crs.backend.user;

/** 학적상태(student.std_stat). 졸업생·휴학생은 수강신청에서 걸러집니다. */
public enum StdStat {
    ATTENDING, // 재학
    ON_LEAVE, // 휴학
    GRADUATED // 졸업
}
