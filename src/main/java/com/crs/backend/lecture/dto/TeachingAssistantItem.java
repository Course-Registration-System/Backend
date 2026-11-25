package com.crs.backend.lecture.dto;

/** 조교 한 명. email 은 없으면 null 입니다. */
public record TeachingAssistantItem(String stdNo, String usrNm, String deptNm, int yrLvl, String email) {}
