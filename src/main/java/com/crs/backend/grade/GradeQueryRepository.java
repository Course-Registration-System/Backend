package com.crs.backend.grade;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 다른 모듈에 주는 성적 조회(GradeQuery)의 SQL. 확정된 성적만 읽습니다. */
@Repository
public class GradeQueryRepository {

    private final JdbcTemplate jdbc;

    public GradeQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 그 과목을 여러 번 들었으면 가장 최근 학기의 확정 성적. 학기 순서는 학기번호가 아니라 연도와 학기구분(1학기 → 여름 → 2학기 → 겨울)으로
     * 정합니다({@link SemesterOrder}). 학기번호는 등록 순서일 뿐 시간 순서가 아니기 때문입니다.
     */
    public Optional<String> findLatestConfirmedGrade(String stdNo, String crsCd) {
        return jdbc.queryForList("""
                        SELECT g.ltr_grd
                          FROM grade g
                          JOIN enrollment e ON e.std_no = g.std_no AND e.lec_no = g.lec_no
                          JOIN lecture l ON l.lec_no = g.lec_no
                          JOIN semester s ON s.sem_no = l.sem_no
                         WHERE g.std_no = ? AND l.crs_cd = ?
                           AND l.grd_stat = 'CONFIRMED' AND e.enrl_stat = 'ENROLLED' AND g.ltr_grd IS NOT NULL
                         ORDER BY %s DESC
                         LIMIT 1
                        """.formatted(SemesterOrder.SQL), String.class, stdNo, crsCd).stream()
                .findFirst();
    }
}
