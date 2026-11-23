package com.crs.backend.grade.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 성적 확정(GRD-02). 강의 하나만 확정할 때도 목록에 하나를 넣습니다. 중복이 있으면 400 입니다. */
public record GradeConfirmRequest(@NotEmpty List<@NotNull Integer> lecNos) {}
