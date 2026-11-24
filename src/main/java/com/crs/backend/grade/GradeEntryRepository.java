package com.crs.backend.grade;

import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import com.crs.backend.lecture.LecStat;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 성적 입력(GRD-01) SQL. grade 와 lecture 의 성적상태(grd_stat)는 이 모듈이 씁니다. enrollment·user_account 는 수강신청·계정 모듈,
 * course 는 과목 관리 소관이라 읽기만 합니다.
 */
@Repository
public class GradeEntryRepository {

    /** 성적 입력에 필요한 강의 정보. 반영 비율은 등록 전이면 모두 null 입니다. */
    public record LectureRow(
            int lecNo,
            int semNo,
            String crsCd,
            String crsNm,
            int sectNo,
            LecStat lecStat,
            EvalType evalType,
            CmplType cmplType,
            boolean engLec,
            boolean relGrdExempt,
            Integer midWt,
            Integer finWt,
            Integer hwWt,
            Integer attWt,
            GrdStat grdStat) {}

    /** 수강생 한 명과 저장된 성적. 항목 점수는 소수 셋째 자리까지(DECIMAL(6,3)), 총점은 정수입니다. 성적 행이 없으면 점수·등급이 모두 null 입니다. */
    public record StudentRow(
            String stdNo,
            String usrNm,
            boolean retake,
            BigDecimal midScr,
            BigDecimal finScr,
            BigDecimal hwScr,
            BigDecimal attScr,
            Integer totScr,
            String ltrGrd) {}

    private static final String LECTURE_SELECT = """
            SELECT l.lec_no, l.sem_no, l.crs_cd, c.crs_nm, l.sect_no, l.lec_stat, c.eval_type, c.cmpl_type,
                   l.eng_lec_yn, l.rel_grd_exmp_yn, l.mid_wt, l.fin_wt, l.hw_wt, l.att_wt, l.grd_stat
              FROM lecture l
              JOIN course c ON c.crs_cd = l.crs_cd
             WHERE l.lec_no = :lecNo
            """;

    private static final RowMapper<LectureRow> LECTURE = (rs, rowNum) -> new LectureRow(
            rs.getInt("lec_no"),
            rs.getInt("sem_no"),
            rs.getString("crs_cd"),
            rs.getString("crs_nm"),
            rs.getInt("sect_no"),
            LecStat.valueOf(rs.getString("lec_stat")),
            EvalType.valueOf(rs.getString("eval_type")),
            CmplType.valueOf(rs.getString("cmpl_type")),
            "Y".equals(rs.getString("eng_lec_yn")),
            "Y".equals(rs.getString("rel_grd_exmp_yn")),
            nullableInt(rs, "mid_wt"),
            nullableInt(rs, "fin_wt"),
            nullableInt(rs, "hw_wt"),
            nullableInt(rs, "att_wt"),
            GrdStat.valueOf(rs.getString("grd_stat")));

    private static final RowMapper<StudentRow> STUDENT = (rs, rowNum) -> new StudentRow(
            rs.getString("std_no"),
            rs.getString("usr_nm"),
            "Y".equals(rs.getString("retake_yn")),
            rs.getBigDecimal("mid_scr"),
            rs.getBigDecimal("fin_scr"),
            rs.getBigDecimal("hw_scr"),
            rs.getBigDecimal("att_scr"),
            nullableInt(rs, "tot_scr"),
            rs.getString("ltr_grd"));

    private final NamedParameterJdbcTemplate jdbc;

    public GradeEntryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 강의. 없으면 비어 있습니다. */
    public Optional<LectureRow> findLecture(int lecNo) {
        return jdbc.query(LECTURE_SELECT, params(lecNo), LECTURE).stream().findFirst();
    }

    /**
     * 강의 행을 잠그고 읽습니다. 교수와 조교가 동시에 저장·제출해도 성적상태가 꼬이지 않게, 저장·제출은 트랜잭션의 첫 조회로 이것을 부릅니다(잠금 뒤의
     * 일반 조회가 다른 트랜잭션이 커밋한 성적을 보도록).
     */
    public Optional<LectureRow> lockLecture(int lecNo) {
        return jdbc.query(LECTURE_SELECT + " FOR UPDATE OF l", params(lecNo), LECTURE).stream()
                .findFirst();
    }

    /** 수강생(신청상태 '신청')과 저장된 성적, 학번 순. */
    public List<StudentRow> findStudents(int lecNo) {
        return jdbc.query("""
                SELECT e.std_no, u.usr_nm, e.retake_yn,
                       g.mid_scr, g.fin_scr, g.hw_scr, g.att_scr, g.tot_scr, g.ltr_grd
                  FROM enrollment e
                  JOIN user_account u ON u.usr_no = e.std_no
                  LEFT JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
                 WHERE e.lec_no = :lecNo
                   AND e.enrl_stat = 'ENROLLED'
                 ORDER BY e.std_no
                """, params(lecNo), STUDENT);
    }

    /** 학생 한 명의 원점수를 저장합니다. 행이 없으면 만들고(수강신청은 성적 행을 만들지 않음) 있으면 원점수 네 개를 바꿉니다. */
    public void saveRawScores(
            int lecNo, String stdNo, BigDecimal midScr, BigDecimal finScr, BigDecimal hwScr, BigDecimal attScr) {
        jdbc.update(
                """
                INSERT INTO grade (std_no, lec_no, mid_scr, fin_scr, hw_scr, att_scr)
                VALUES (:stdNo, :lecNo, :midScr, :finScr, :hwScr, :attScr) AS new
                ON DUPLICATE KEY UPDATE
                    mid_scr = new.mid_scr, fin_scr = new.fin_scr, hw_scr = new.hw_scr, att_scr = new.att_scr
                """,
                params(lecNo)
                        .addValue("stdNo", stdNo)
                        .addValue("midScr", midScr)
                        .addValue("finScr", finScr)
                        .addValue("hwScr", hwScr)
                        .addValue("attScr", attScr));
    }

    /** 강의 전체의 상대점수·등급을 비웁니다. 원점수가 하나라도 바뀌면 순위가 바뀔 수 있어서입니다. */
    public void clearResults(int lecNo) {
        jdbc.update("UPDATE grade SET tot_scr = NULL, ltr_grd = NULL WHERE lec_no = :lecNo", params(lecNo));
    }

    /** 제출할 때 계산한 학생 한 명의 상대점수·등급을 저장합니다. */
    public void saveResult(int lecNo, String stdNo, int totScr, String ltrGrd) {
        jdbc.update(
                "UPDATE grade SET tot_scr = :totScr, ltr_grd = :ltrGrd WHERE lec_no = :lecNo AND std_no = :stdNo",
                params(lecNo)
                        .addValue("stdNo", stdNo)
                        .addValue("totScr", totScr)
                        .addValue("ltrGrd", ltrGrd));
    }

    public void updateGrdStat(int lecNo, GrdStat grdStat) {
        jdbc.update(
                "UPDATE lecture SET grd_stat = :grdStat WHERE lec_no = :lecNo",
                params(lecNo).addValue("grdStat", grdStat.name()));
    }

    private static MapSqlParameterSource params(int lecNo) {
        return new MapSqlParameterSource("lecNo", lecNo);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
