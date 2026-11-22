package com.crs.backend.course.dto;

import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import java.math.BigDecimal;
import java.util.List;

/** 과목 한 건과 선수과목 목록. */
public record CourseResponse(
        String crsCd,
        String crsNm,
        int deptNo,
        String deptNm,
        BigDecimal crdt,
        CmplType cmplType,
        EvalType evalType,
        Integer rcmdYrLvl,
        List<PrerequisiteResponse> prerequisites) {}
