package com.crs.backend.academic;

import com.crs.backend.academic.dto.DashboardResponse.FullLecture;
import com.crs.backend.academic.dto.DashboardResponse.MissingPlanLecture;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 대시보드(ACA-04)의 집계 SQL. 읽기만 하고, 모두 개설 상태(OPEN)인 강의만 셉니다. */
@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countOpenLectures(int semNo) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM lecture WHERE sem_no = ? AND lec_stat = 'OPEN'", Integer.class, semNo);
    }

    public List<FullLecture> findFullLectures(int semNo) {
        return jdbc.query(
                """
                SELECT l.lec_no, c.crs_nm, l.sect_no, u.usr_nm AS prof_nm, l.cap, l.enrl_cnt
                  FROM lecture l
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND l.enrl_cnt >= l.cap
                 ORDER BY c.crs_nm, l.sect_no
                """,
                (rs, rowNum) -> new FullLecture(
                        rs.getInt("lec_no"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getString("prof_nm"),
                        rs.getInt("cap"),
                        rs.getInt("enrl_cnt")),
                semNo);
    }

    /** 강의실이 없는 대면 수업 칸이 하나라도 있는 강의 수(ACA-03). 기간과 상관없이 셉니다. */
    public int countUnassignedLectures(int semNo) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM lecture l
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN'
                   AND EXISTS (SELECT 1 FROM lecture_time_slot t
                                WHERE t.lec_no = l.lec_no AND t.cls_type = 'OFFLINE' AND t.room_cd IS NULL)
                """, Integer.class, semNo);
    }

    /** 계획서 파일이 없거나, 등급제 과목인데 반영비율이 없는 강의(LEC-03). P/F 과목은 반영비율이 없어도 됩니다. */
    public List<MissingPlanLecture> findMissingPlanLectures(int semNo) {
        return jdbc.query(
                """
                SELECT l.lec_no, c.crs_nm, l.sect_no, l.prof_no, u.usr_nm AS prof_nm, u.email,
                       l.plan_file IS NULL AS no_plan_file,
                       c.eval_type = 'GRADE' AND l.mid_wt IS NULL AS no_weights
                  FROM lecture l
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN'
                   AND (l.plan_file IS NULL OR (c.eval_type = 'GRADE' AND l.mid_wt IS NULL))
                 ORDER BY u.usr_nm, c.crs_nm, l.sect_no
                """,
                (rs, rowNum) -> new MissingPlanLecture(
                        rs.getInt("lec_no"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getString("prof_no"),
                        rs.getString("prof_nm"),
                        rs.getString("email"),
                        rs.getBoolean("no_plan_file"),
                        rs.getBoolean("no_weights")),
                semNo);
    }
}
