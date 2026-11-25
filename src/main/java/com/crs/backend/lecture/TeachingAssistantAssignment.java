package com.crs.backend.lecture;

/** teaching_assistant_assignment 테이블 한 행. stdNo 학생이 lecNo 강의의 조교로 배정된 건입니다. */
public record TeachingAssistantAssignment(int lecNo, String stdNo) {}
