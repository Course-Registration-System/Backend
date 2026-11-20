package com.crs.backend.academic;

import java.util.Optional;

/**
 * 학사일정·최대학점 판정(ACA-01). 다른 모듈은 학사일정 테이블을 직접 읽지 않고 이 인터페이스를 씁니다.
 *
 * <p>구현은 academic 모듈이 맡습니다. 현재 시각은 구현이 주입받은 Clock 으로 판단합니다.
 */
public interface SchedulePolicy {

    /** 신청 대상 학기: 지금이 장바구니 시작부터 정정 종료 사이인 학기. 없으면 비어 있습니다. */
    Optional<Integer> registrationSemester();

    /** 지금이 그 학기의 장바구니 기간인지. */
    boolean cartOpen(int semNo);

    /** 지금 그 학년 학생이 신청·취소할 수 있는지: 본인 학년 수강신청일, 전체 수강신청일, 정정 기간. */
    boolean enrollOpen(int semNo, int yrLvl);

    /** 학생의 그 학기 최대학점. 산정 결과가 없으면 정규학기 18, 계절학기는 재학 6·휴학 3 입니다. */
    int maxCredit(String stdNo, int semNo);
}
