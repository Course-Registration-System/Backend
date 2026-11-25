package com.crs.backend.lecture.dto;

import jakarta.validation.constraints.NotBlank;

/** 조교 추가. 조교로 지정할 학생의 학번입니다. */
public record TeachingAssistantRequest(@NotBlank String stdNo) {}
