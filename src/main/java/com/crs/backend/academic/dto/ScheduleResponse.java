package com.crs.backend.academic.dto;

import com.crs.backend.academic.SchdType;
import java.time.LocalDateTime;

/** tgtYrLvl 이 null 이면 전 학년 일정입니다. */
public record ScheduleResponse(
        int schdNo, int semNo, SchdType schdType, Integer tgtYrLvl, LocalDateTime startDtm, LocalDateTime endDtm) {}
