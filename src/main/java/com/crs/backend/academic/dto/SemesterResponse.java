package com.crs.backend.academic.dto;

import com.crs.backend.academic.SemType;

public record SemesterResponse(int semNo, int yr, SemType semType, boolean regular, boolean current) {}
