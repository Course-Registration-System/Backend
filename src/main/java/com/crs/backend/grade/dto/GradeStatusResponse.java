package com.crs.backend.grade.dto;

import com.crs.backend.grade.GrdStat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 학기별 강의 성적상태(GRD-02). gradeEnd 는 성적 입력 기간의 끝(이 시각부터 확정 가능, 일정이 없으면 null), confirmable 은 지금 확정할
 * 수 있는 시기인지입니다. counts 는 폐강을 뺀 학기 전체의 성적상태별 강의 수이고, lectures 는 grdStat 로 거른 목록입니다.
 */
public record GradeStatusResponse(
        int semNo,
        int yr,
        String semType,
        LocalDateTime gradeEnd,
        boolean confirmable,
        Map<GrdStat, Integer> counts,
        List<GradeStatusLecture> lectures) {}
