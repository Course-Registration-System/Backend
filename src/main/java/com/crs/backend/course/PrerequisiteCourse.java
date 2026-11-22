package com.crs.backend.course;

/** prerequisite_course 테이블 한 행. crsCd 를 들으려면 prereqCrsCd 를 minReqGrd 이상으로 이수해야 합니다. */
public record PrerequisiteCourse(String crsCd, String prereqCrsCd, String minReqGrd) {

    /** 최소취득등급을 정하지 않았을 때의 값. F 가 아니면 인정합니다. */
    public static final String DEFAULT_MIN_REQ_GRD = "D-";
}
