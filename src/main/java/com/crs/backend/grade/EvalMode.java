package com.crs.backend.grade;

/** 강의의 평가 방식(GRD-01 "상대평가, 절대평가, P/F 세 가지"). 과목 평가방식·수강인원·영어강의·상대평가 제외 표시로 정해집니다. */
public enum EvalMode {
    /** 상대평가: 등급제, 수강인원 10명 이상이고 제외 대상이 아님. A·B 비율 제한 */
    RELATIVE,
    /** 절대평가: 등급제 중 수강인원 9명 이하, 전공 영어강의, 상대평가 제외 강의. 비율 제한 없음 */
    ABSOLUTE,
    /** P/F 과목. 점수 없이 P 또는 F */
    PASS_FAIL
}
