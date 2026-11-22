package com.crs.backend.academic.dto;

import com.crs.backend.academic.SchdType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 학사일정 등록. tgtYrLvl 을 비우면 전 학년에 적용합니다. */
public record ScheduleRequest(
        @NotNull SchdType schdType,
        @Min(1) @Max(4) Integer tgtYrLvl,
        @NotNull LocalDateTime startDtm,
        @NotNull LocalDateTime endDtm) {}
