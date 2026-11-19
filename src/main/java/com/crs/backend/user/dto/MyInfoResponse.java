package com.crs.backend.user.dto;

import com.crs.backend.common.auth.Role;

/** 화면이 역할별 메뉴를 고를 때 쓰는 정보. ta 가 true 면 학생 메뉴에 강의 운영·성적 메뉴를 더 보여 줍니다. */
public record MyInfoResponse(String usrNo, String usrNm, Role role, boolean ta) {}
