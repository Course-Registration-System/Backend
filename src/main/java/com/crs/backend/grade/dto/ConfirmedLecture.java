package com.crs.backend.grade.dto;

import com.crs.backend.grade.GrdStat;
import java.time.LocalDateTime;

/** 확정한 강의 한 건. */
public record ConfirmedLecture(int lecNo, String crsNm, GrdStat grdStat, LocalDateTime cnfmDtm) {}
