package com.crs.backend.user.dto;

/** 이후 요청마다 accessToken 을 Authorization: Bearer 헤더에 넣어 보냅니다. */
public record LoginResponse(String accessToken, MyInfoResponse user) {}
