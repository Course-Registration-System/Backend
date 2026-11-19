package com.crs.backend.lecture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 강의 개설. engLec 은 영어강의(EN), relGrdExmp 는 상대평가 제외 여부입니다. */
public record LectureCreateRequest(
        @NotBlank String crsCd,
        @NotNull Integer semNo,
        @NotBlank String profNo,
        @NotNull @Min(1) @Max(99) Integer sectNo,
        @NotNull @Min(1) @Max(999) Integer cap,
        boolean engLec,
        boolean relGrdExmp,
        @NotEmpty List<@Valid TimeSlotRequest> timeSlots) {}
