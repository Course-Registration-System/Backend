package com.crs.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CartAddRequest(@NotNull Integer lecNo) {}
