package com.crs.backend.course;

import java.math.BigDecimal;

/** course 테이블 한 행. rcmdYrLvl 은 없으면 null 입니다. */
public record Course(
        String crsCd,
        int deptNo,
        String crsNm,
        BigDecimal crdt,
        CmplType cmplType,
        EvalType evalType,
        Integer rcmdYrLvl) {}
