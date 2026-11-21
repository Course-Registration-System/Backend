package com.crs.backend.course;

/** 이수구분(course.cmpl_type). */
public enum CmplType {
    /** 전공기초 */
    MAJOR_BASIC,
    /** 전공필수 */
    MAJOR_REQ,
    /** 전공선택 */
    MAJOR_ELEC,
    /** 교양필수 */
    GEN_REQ,
    /** 교양선택 */
    GEN_ELEC,
    /** 교직 */
    TEACHING,
    /** 일반선택 */
    FREE_ELEC,
    /** 채플. 0.5학점, P/F 로만 평가 */
    CHAPEL
}
