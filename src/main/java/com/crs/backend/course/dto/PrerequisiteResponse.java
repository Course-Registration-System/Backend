package com.crs.backend.course.dto;

/** 선수과목 하나. 요청(PrerequisiteRequest)과 같은 이름을 씁니다. */
public record PrerequisiteResponse(String prereqCrsCd, String prereqCrsNm, String minReqGrd) {}
