package com.crs.backend.grade;

import com.crs.backend.academic.MaxCreditRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 성적 조회(GRD-03)의 SQL. 신청상태가 '신청'이고 성적이 확정된 강의의 등급만 읽습니다. */
@Repository
public class GradeReportRepository {

    /** 성적표 한 줄과 그 줄이 속한 학기. */
    public record Row(
            int semNo,
            int yr,
            String semType,
            int lecNo,
            String crsCd,
            String crsNm,
            BigDecimal crdt,
            String cmplType,
            String evalType,
            String ltrGrd,
            BigDecimal gradePoint,
            boolean retake,
            boolean counted) {}

    // 같은 과목을 여러 번 들었으면 가장 최근 시도만 counted = 1. 채플은 학기마다 따로 이수하므로 언제나 1
    private static final String SQL =
            """
            SELECT s.sem_no, s.yr, s.sem_type, l.lec_no, l.crs_cd, c.crs_nm, c.crdt, c.cmpl_type, c.eval_type,
                   g.ltr_grd, e.retake_yn,
                   CASE WHEN c.eval_type = 'GRADE' THEN %s END AS grade_point,
                   CASE WHEN c.cmpl_type = 'CHAPEL' THEN 1
                        WHEN ROW_NUMBER() OVER (PARTITION BY l.crs_cd ORDER BY %s DESC) = 1 THEN 1
                        ELSE 0 END AS counted
              FROM enrollment e
              JOIN lecture l ON l.lec_no = e.lec_no
              JOIN semester s ON s.sem_no = l.sem_no
              JOIN course c ON c.crs_cd = l.crs_cd
              JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
             WHERE e.std_no = ?
               AND e.enrl_stat = 'ENROLLED'
               AND l.grd_stat = 'CONFIRMED'
               AND g.ltr_grd IS NOT NULL
             ORDER BY %s, c.crs_nm, l.sect_no
            """.formatted(GradePoint.sql("g.ltr_grd"), MaxCreditRepository.SEM_ORD, MaxCreditRepository.SEM_ORD);

    private final JdbcTemplate jdbc;

    public GradeReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> findConfirmed(String stdNo) {
        return jdbc.query(
                SQL,
                (rs, rowNum) -> new Row(
                        rs.getInt("sem_no"),
                        rs.getInt("yr"),
                        rs.getString("sem_type"),
                        rs.getInt("lec_no"),
                        rs.getString("crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getBigDecimal("crdt"),
                        rs.getString("cmpl_type"),
                        rs.getString("eval_type"),
                        rs.getString("ltr_grd"),
                        rs.getBigDecimal("grade_point"),
                        "Y".equals(rs.getString("retake_yn")),
                        rs.getInt("counted") == 1),
                stdNo);
    }
}
