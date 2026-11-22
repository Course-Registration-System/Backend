package com.crs.backend.lecture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 강의 수정. 과목·학기·분반은 바꾸지 않습니다. timeSlots 는 기존 칸을 모두 이 목록으로 바꿉니다.
 *
 * <p>수강신청 기간이 시작된 뒤에는 timeSlots 를 기존과 똑같이 보내야 합니다(정원·교수만 수정).
 */
public record LectureUpdateRequest(
        @NotBlank String profNo,
        @NotNull @Min(1) @Max(999) Integer cap,
        boolean engLec,
        boolean relGrdExmp,
        @NotEmpty List<@Valid TimeSlotRequest> timeSlots) {}
