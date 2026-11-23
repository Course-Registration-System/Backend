package com.crs.backend.grade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 성적 저장(GRD-01). 넣은 학생만 바뀌고 넣지 않은 학생은 그대로입니다. 같은 학번을 두 번 넣으면 400 입니다. */
public record GradeSaveRequest(@NotEmpty List<@NotNull @Valid GradeInput> grades) {}
