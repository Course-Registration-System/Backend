package com.crs.backend.grade;

import java.util.Optional;

/** 성적 조회. 다른 모듈은 성적 테이블을 직접 읽지 않고 이 인터페이스를 씁니다. 구현은 성적 모듈이 맡습니다. */
public interface GradeQuery {

    /** 그 과목의 가장 최근 확정 성적(A+ ~ F, P). 확정된 성적이 없으면 비어 있습니다. */
    Optional<String> latestConfirmedGrade(String stdNo, String crsCd);
}
