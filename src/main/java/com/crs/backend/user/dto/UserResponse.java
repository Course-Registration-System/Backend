package com.crs.backend.user.dto;

import com.crs.backend.common.auth.Role;
import com.crs.backend.user.AcctStat;
import com.crs.backend.user.StdStat;

/** 계정 목록 한 줄. 학생이 아니면 yrLvl·stdStat 이, 교수가 아니면 office 가 null 입니다. */
public record UserResponse(
        String usrNo,
        String usrNm,
        String email,
        Role role,
        AcctStat acctStat,
        int deptNo,
        String deptNm,
        Integer yrLvl,
        StdStat stdStat,
        String office) {}
