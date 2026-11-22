package com.crs.backend.lecture.dto;

import java.util.List;

/** 강의 검색 한 페이지. semNo 는 검색한 학기, total 은 조건에 맞는 전체 강의 수입니다. */
public record LectureSearchResponse(int semNo, int page, int size, long total, List<LectureSearchItem> items) {}
