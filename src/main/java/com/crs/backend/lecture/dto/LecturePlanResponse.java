package com.crs.backend.lecture.dto;

import com.crs.backend.course.EvalType;
import java.time.LocalDateTime;

/**
 * 강의계획서(LEC-03). hasPlanFile 은 계획서가 등록됐는지(파일은 내려받기 API 로 받음), weights 는 등록 전이거나 P/F 과목이면 null
 * 입니다. deadline 은 등록·수정 마감(장바구니 시작, 일정이 없으면 null), editable 은 지금 등록·수정할 수 있는지(마감 전, 폐강 아님)입니다.
 */
public record LecturePlanResponse(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        EvalType evalType,
        boolean hasPlanFile,
        PlanWeights weights,
        LocalDateTime deadline,
        boolean editable) {}
