package com.crs.backend.grade;

/**
 * 학기 순서값 SQL 식. 학기 순서는 sem_no 가 아니라 연도와 학기구분으로 정합니다. 성적 조회와 최대학점 산정이 같이 씁니다. 여러 곳에 따로 쓰면
 * 한쪽만 고쳐지는 일이 생기기 때문입니다.
 */
public final class SemesterOrder {

    /** 연도×10 + (1학기 1, 여름 2, 2학기 3, 겨울 4). 학기 테이블 별칭은 s 입니다. 학기구분 선언 순서와 같습니다. */
    public static final String SQL = "(s.yr * 10 + FIELD(s.sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))";

    private SemesterOrder() {}
}
