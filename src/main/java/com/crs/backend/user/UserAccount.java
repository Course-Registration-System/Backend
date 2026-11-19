package com.crs.backend.user;

import com.crs.backend.common.auth.Role;

/** user_account 테이블 한 행. pwd 는 BCrypt 해시입니다. */
public record UserAccount(String usrNo, String pwd, String usrNm, String email, Role role, AcctStat acctStat) {}
