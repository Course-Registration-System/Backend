package com.crs.backend.lecture.dto;

import java.util.List;

/**
 * 담당 강의 목록(LEC-01). semNo·yr·semType 은 조회한 학기입니다. 현재 학기가 지정되지 않아 학기를 정하지 못하면 셋 다 null, 없는 학기번호면
 * semNo 만 채우고 yr·semType 은 null 이며, 두 경우 모두 lectures 는 빈 목록입니다.
 */
public record TeachingLectureListResponse(
        Integer semNo, Integer yr, String semType, List<TeachingLectureItem> lectures) {}
