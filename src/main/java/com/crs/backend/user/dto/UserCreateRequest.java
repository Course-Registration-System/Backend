package com.crs.backend.user.dto;

import com.crs.backend.common.auth.Role;
import com.crs.backend.user.StdStat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 계정 등록. role 은 STUDENT 또는 PROFESSOR 입니다(관리자 계정은 시드로만 만듭니다).
 *
 * <p>usrNo 는 숫자만: 학부생 8자리, 대학원생 10자리(조교로만 씀), 교수 6자리({@link com.crs.backend.user.UserNo}).
 *
 * <p>학생만 쓰는 값: yrLvl(필수), stdStat(비우면 재학). 교수만 쓰는 값: office.
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 10) String usrNo,
        @NotBlank String password,
        @NotBlank @Size(max = 50) String usrNm,
        @Email @Size(max = 100) String email,
        @NotNull Role role,
        @NotNull Integer deptNo,
        @Min(1) @Max(4) Integer yrLvl,
        StdStat stdStat,
        @Size(max = 50) String office) {}
