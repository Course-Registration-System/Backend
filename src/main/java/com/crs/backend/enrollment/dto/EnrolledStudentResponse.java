package com.crs.backend.enrollment.dto;

import com.crs.backend.enrollment.ProcType;
import java.time.LocalDateTime;

/** 강의를 신청한 학생 한 명. procType 으로 학생 본인 처리와 관리자 처리를 구분합니다. */
public record EnrolledStudentResponse(
        String stdNo, String stdNm, int yrLvl, boolean retake, ProcType procType, LocalDateTime enrlDtm) {}
