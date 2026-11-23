package com.crs.backend.lecture.dto;

import java.util.List;

/**
 * 담당 강의 목록(LEC-01). semNo·yr·semType 은 조회한 학기입니다. 학기를 찾지 못하면 이 응답 대신 404 SEMESTER_NOT_FOUND 입니다.
 */
public record TeachingLectureListResponse(int semNo, int yr, String semType, List<TeachingLectureItem> lectures) {}
