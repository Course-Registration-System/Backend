package com.crs.backend.lecture.dto;

import java.util.List;

/** 수강생 명단(LEC-02). 신청상태가 '신청'인 학생만 학번 순으로 담고, 없으면 빈 목록입니다. */
public record TeachingStudentListResponse(int lecNo, List<TeachingStudentItem> students) {}
