package com.crs.backend.grade.dto;

import com.crs.backend.grade.EvalMode;
import com.crs.backend.grade.GrdStat;
import java.util.List;

/**
 * 강의별 성적표(GRD-01). editable 은 지금 저장·제출할 수 있는지(성적 입력 기간 안, 확정 전, 폐강 아님)입니다. weights 는 P/F 과목이거나
 * 등록 전이면 null, ratio 는 상대평가 강의만 있습니다. students 는 수강생(신청상태 '신청')을 학번 순으로 담습니다.
 */
public record GradeSheetResponse(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        GrdStat grdStat,
        boolean editable,
        EvalMode evalMode,
        GradeWeights weights,
        GradeRatio ratio,
        List<GradeSheetStudent> students) {}
