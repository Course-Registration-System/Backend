package com.crs.backend.lecture;

/** 강의 검색 조건. 학기를 뺀 값은 null 이면 조건에서 빠집니다. 문자열은 앞뒤 공백을 뗀 값이고 빈 문자열은 null 입니다. */
public record LectureSearchCondition(
        int semNo, Integer deptNo, Integer yrLvl, String cmplType, String crsNm, String profNm) {}
