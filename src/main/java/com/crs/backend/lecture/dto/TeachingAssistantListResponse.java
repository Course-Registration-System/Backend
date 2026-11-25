package com.crs.backend.lecture.dto;

import java.util.List;

/** 강의별 조교 목록(LEC-04). 학번 순이며, 조교가 없으면 assistants 는 빈 목록입니다. */
public record TeachingAssistantListResponse(int lecNo, List<TeachingAssistantItem> assistants) {}
