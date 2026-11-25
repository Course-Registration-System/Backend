package com.crs.backend.grade.dto;

import com.crs.backend.grade.GrdStat;
import java.time.LocalDateTime;

/**
 * 성적 확정 화면의 강의 한 건(GRD-02). 담당 교수 이름·이메일은 미입력 강의 교수에게 연락할 때 씁니다. enrlCnt 는 신청상태 '신청' 인원,
 * cnfmDtm 은 확정 전이면 null 입니다.
 */
public record GradeStatusLecture(
        int lecNo,
        String crsCd,
        String crsNm,
        int sectNo,
        String profNo,
        String profNm,
        String profEmail,
        int enrlCnt,
        GrdStat grdStat,
        LocalDateTime cnfmDtm) {}
