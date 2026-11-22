package com.crs.backend.enrollment.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminEnrollRequest(@NotBlank String stdNo) {}
