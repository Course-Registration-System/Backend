package com.crs.backend.lecture;

import com.crs.backend.course.EvalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 강의계획서(LEC-03) SQL. lecture 의 계획서 파일 경로·평가 반영 비율은 이 모듈이 씁니다. course 는 과목 관리 소관이라 읽기만
 * 합니다.
 */
@Repository
public class LecturePlanRepository {

    /** 계획서 관리에 필요한 강의 정보. 반영 비율은 등록 전이면 모두 null 입니다. */
    public record PlanRow(
            int lecNo,
            int semNo,
            String crsCd,
            String crsNm,
            int sectNo,
            LecStat lecStat,
            EvalType evalType,
            String planFile,
            Integer midWt,
            Integer finWt,
            Integer hwWt,
            Integer attWt) {}

    private static final String SELECT = """
            SELECT l.lec_no, l.sem_no, l.crs_cd, c.crs_nm, l.sect_no, l.lec_stat, c.eval_type,
                   l.plan_file, l.mid_wt, l.fin_wt, l.hw_wt, l.att_wt
              FROM lecture l
              JOIN course c ON c.crs_cd = l.crs_cd
             WHERE l.lec_no = :lecNo
            """;

    private static final RowMapper<PlanRow> PLAN = (rs, rowNum) -> new PlanRow(
            rs.getInt("lec_no"),
            rs.getInt("sem_no"),
            rs.getString("crs_cd"),
            rs.getString("crs_nm"),
            rs.getInt("sect_no"),
            LecStat.valueOf(rs.getString("lec_stat")),
            EvalType.valueOf(rs.getString("eval_type")),
            rs.getString("plan_file"),
            nullableInt(rs, "mid_wt"),
            nullableInt(rs, "fin_wt"),
            nullableInt(rs, "hw_wt"),
            nullableInt(rs, "att_wt"));

    private final NamedParameterJdbcTemplate jdbc;

    public LecturePlanRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PlanRow> find(int lecNo) {
        return jdbc.query(SELECT, params(lecNo), PLAN).stream().findFirst();
    }

    /** 강의 행을 잠그고 읽습니다. 교수와 조교가 동시에 올려도 하나씩 처리됩니다. */
    public Optional<PlanRow> lock(int lecNo) {
        return jdbc.query(SELECT + " FOR UPDATE OF l", params(lecNo), PLAN).stream()
                .findFirst();
    }

    public void updatePlanFile(int lecNo, String planFile) {
        jdbc.update(
                "UPDATE lecture SET plan_file = :planFile WHERE lec_no = :lecNo",
                params(lecNo).addValue("planFile", planFile));
    }

    /** 네 비율을 함께 바꿉니다(ck_lecture_weight_all_or_none·ck_lecture_weight_sum). */
    public void updateWeights(int lecNo, int midWt, int finWt, int hwWt, int attWt) {
        jdbc.update(
                """
                UPDATE lecture SET mid_wt = :midWt, fin_wt = :finWt, hw_wt = :hwWt, att_wt = :attWt
                 WHERE lec_no = :lecNo
                """,
                params(lecNo)
                        .addValue("midWt", midWt)
                        .addValue("finWt", finWt)
                        .addValue("hwWt", hwWt)
                        .addValue("attWt", attWt));
    }

    private static MapSqlParameterSource params(int lecNo) {
        return new MapSqlParameterSource("lecNo", lecNo);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
