package com.crs.backend.academic;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 학사일정·최대학점 판정(ACA-01). 다른 모듈은 학사일정 테이블을 직접 읽지 않고 이 인터페이스를 씁니다.
 *
 * <p>구현은 academic 모듈이 맡습니다({@link AcademicSchedulePolicy}). 현재 시각은 구현이 주입받은 Clock 으로 판단합니다.
 */
public interface SchedulePolicy {

    /**
     * 신청 대상 학기: 장바구니·수강신청·정정 일정이 아직 끝나지 않은 학기 중 가장 이른 학기. 장바구니 기간 전에도 다음 학기를 돌려줍니다
     * (강의 검색이 미리 볼 수 있게). 정정까지 모두 끝나 대상이 없으면 비어 있습니다.
     */
    Optional<Integer> registrationSemester();

    /**
     * 조회 화면의 학기(D24). semNo 를 주면 그 학기이고 없는 학기면 SEMESTER_NOT_FOUND 입니다. 비우면 신청 대상 학기, 신청 대상 학기가 없으면(정정까지
     * 끝남) 현재 학기입니다.
     */
    int semesterOrDefault(Integer semNo);

    /** 지금이 그 학기의 장바구니 기간인지. 장바구니는 전 학년이 같은 기간입니다. */
    boolean cartOpen(int semNo);

    /** 지금 그 학년 학생이 신청·취소할 수 있는지: 본인 학년 수강신청일, 전체 수강신청일, 정정 기간. */
    boolean enrollOpen(int semNo, int yrLvl);

    /** 학생의 그 학기 최대학점. 산정 결과가 없으면 정규학기 18, 계절학기는 재학 6·휴학 3 입니다. */
    int maxCredit(String stdNo, int semNo);

    /** 장바구니 기간의 시작(강의 개설·강의계획서 마감). 장바구니 일정이 없으면 수강신청 시작으로 대신합니다. */
    Optional<LocalDateTime> cartStart(int semNo);

    /** 학년별 수강신청일 중 가장 이른 시작(강의시간·수업유형 변경 마감). */
    Optional<LocalDateTime> enrollStart(int semNo);

    /** 정정 기간의 시작(강의실 미배정 허용 마감). */
    Optional<LocalDateTime> adjustStart(int semNo);

    /** 정정 기간의 끝. */
    Optional<LocalDateTime> adjustEnd(int semNo);

    /**
     * 신청 종료: 수강신청·정정 일정 중 가장 늦은 끝(강의실·교수·정원 변경, 폐강, 관리자 수강신청 마감). 정정 일정이 없는 학기는 마지막
     * 수강신청일의 끝이고, 둘 다 없으면 비어 있습니다.
     */
    Optional<LocalDateTime> registrationEnd(int semNo);

    /**
     * 지금이 그 학기의 성적 입력 기간인지(GRD-01 입력·수정·제출). 시작 시각은 포함, 종료 시각은 제외합니다. 성적입력 일정이 없는 학기는
     * false 입니다.
     */
    boolean gradeOpen(int semNo);

    /**
     * 성적 입력 기간의 끝. 이 시각부터 관리자가 확정할 수 있습니다(GRD-02 "성적 입력 기간 종료 후"). 성적입력 일정이 없는 학기는 비어
     * 있습니다.
     */
    Optional<LocalDateTime> gradeEnd(int semNo);
}
