package com.crs.backend.academic.dto;

import java.util.List;

/** 대시보드(ACA-04). 폐강 강의는 모든 항목에서 뺍니다. */
public record DashboardResponse(
        int semNo,
        int openLectureCount,
        List<FullLecture> fullLectures,
        int unassignedLectureCount,
        List<MissingPlanLecture> missingPlanLectures) {

    /** 신청인원이 정원에 다다른 강의. */
    public record FullLecture(int lecNo, String crsNm, int sectNo, String profNm, int cap, int enrlCnt) {}

    /** 강의계획서 미제출 강의. 계획서 파일이 없거나(noPlanFile), 등급제 과목인데 반영비율이 없습니다(noWeights). */
    public record MissingPlanLecture(
            int lecNo,
            String crsNm,
            int sectNo,
            String profNo,
            String profNm,
            String profEmail,
            boolean noPlanFile,
            boolean noWeights) {}
}
