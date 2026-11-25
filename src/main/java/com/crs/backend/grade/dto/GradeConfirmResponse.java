package com.crs.backend.grade.dto;

import java.util.List;

/** 성적 확정 결과(GRD-02). 요청한 강의를 강의번호 순으로 담습니다. */
public record GradeConfirmResponse(List<ConfirmedLecture> confirmed) {}
