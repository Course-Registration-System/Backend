package com.crs.backend.academic.dto;

import com.crs.backend.user.StdStat;

public record MaxCreditItem(String stdNo, String usrNm, int yrLvl, StdStat stdStat, int maxCrdt) {}
