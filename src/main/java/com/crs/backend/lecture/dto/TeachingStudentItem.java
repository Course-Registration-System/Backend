package com.crs.backend.lecture.dto;

/** 수강생 한 명(LEC-02). 업무기술서 출력 정보(학번, 이름, 학과, 학년, 이메일) 그대로이고, 이메일은 비어 있을 수 있습니다. */
public record TeachingStudentItem(String stdNo, String usrNm, String deptNm, int yrLvl, String email) {}
