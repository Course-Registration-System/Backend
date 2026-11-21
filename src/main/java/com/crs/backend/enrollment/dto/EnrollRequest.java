package com.crs.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollRequest(@NotNull Integer lecNo) {}
