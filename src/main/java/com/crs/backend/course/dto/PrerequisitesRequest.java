package com.crs.backend.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 선수과목 지정. 보낸 목록으로 통째로 바꾸며, 빈 목록이면 모두 해제합니다. */
public record PrerequisitesRequest(@NotNull List<@NotNull @Valid PrerequisiteRequest> prerequisites) {}
