package com.crs.backend.enrollment.dto;

import java.util.List;

/** 강의별 신청 현황(REG-05). */
public record EnrollmentStatusResponse(
        int lecNo, int cap, int enrlCnt, int remaining, List<EnrolledStudentResponse> students) {}
