package com.crs.backend.grade;

import com.crs.backend.grade.dto.GradeStatusLecture;
import com.crs.backend.lecture.LecStat;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 성적 확정(GRD-02) SQL. lecture 의 성적상태·확정자·확정일시는 이 모듈이 씁니다. semester 는 학사 모듈, course 는 과목 관리,
 * user_account 는 계정 모듈 소관이라 읽기만 합니다.
 */
@Repository
public class GradeConfirmRepository {

    /** 학기 한 행. */
    public record SemesterRow(int semNo, int yr, String semType) {}

    /** 확정할 때 잠그고 읽는 강의. */
    public record LockedLecture(int lecNo, int semNo, String crsNm, LecStat lecStat, GrdStat grdStat) {}

    private static final RowMapper<SemesterRow> SEMESTER =
            (rs, rowNum) -> new SemesterRow(rs.getInt("sem_no"), rs.getInt("yr"), rs.getString("sem_type"));

    private static final RowMapper<GradeStatusLecture> STATUS = (rs, rowNum) -> new GradeStatusLecture(
            rs.getInt("lec_no"),
            rs.getString("crs_cd"),
            rs.getString("crs_nm"),
            rs.getInt("sect_no"),
            rs.getString("prof_no"),
            rs.getString("usr_nm"),
            rs.getString("email"),
            rs.getInt("enrl_cnt"),
            GrdStat.valueOf(rs.getString("grd_stat")),
            toLocalDateTime(rs.getTimestamp("cnfm_dtm")));

    private static final RowMapper<LockedLecture> LOCKED = (rs, rowNum) -> new LockedLecture(
            rs.getInt("lec_no"),
            rs.getInt("sem_no"),
            rs.getString("crs_nm"),
            LecStat.valueOf(rs.getString("lec_stat")),
            GrdStat.valueOf(rs.getString("grd_stat")));

    private final NamedParameterJdbcTemplate jdbc;

    public GradeConfirmRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SemesterRow> findSemester(int semNo) {
        return jdbc
                .query(
                        "SELECT sem_no, yr, sem_type FROM semester WHERE sem_no = :semNo",
                        new MapSqlParameterSource("semNo", semNo),
                        SEMESTER)
                .stream()
                .findFirst();
    }

    /** 현재 학기(cur_sem_yn = 'Y'). 지정되지 않았으면 비어 있습니다. */
    public Optional<SemesterRow> findCurrentSemester() {
        return jdbc
                .query(
                        "SELECT sem_no, yr, sem_type FROM semester WHERE cur_sem_yn = 'Y'",
                        new MapSqlParameterSource(),
                        SEMESTER)
                .stream()
                .findFirst();
    }

    /** 학기의 개설 강의(폐강 제외)와 담당 교수, 과목코드·분반 순. 수강인원은 lecture.enrl_cnt(신청상태 '신청' 인원)입니다. */
    public List<GradeStatusLecture> findLectures(int semNo) {
        return jdbc.query("""
                SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, l.prof_no, u.usr_nm, u.email,
                       l.enrl_cnt, l.grd_stat, l.cnfm_dtm
                  FROM lecture l
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE l.sem_no = :semNo
                   AND l.lec_stat = 'OPEN'
                 ORDER BY l.crs_cd, l.sect_no
                """, new MapSqlParameterSource("semNo", semNo), STATUS);
    }

    /**
     * 강의 행들을 강의번호 순으로 잠그고 읽습니다. 순서를 정해 잠가 관리자 두 명이 동시에 확정해도 교착이 생기지 않고, GRD-01 저장(같은 강의
     * 행을 잠금)과도 겹치지 않습니다. 없는 강의는 결과에서 빠집니다.
     */
    public List<LockedLecture> lockLectures(List<Integer> lecNos) {
        return jdbc.query("""
                SELECT l.lec_no, l.sem_no, c.crs_nm, l.lec_stat, l.grd_stat
                  FROM lecture l
                  JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.lec_no IN (:lecNos)
                 ORDER BY l.lec_no
                   FOR UPDATE OF l
                """, new MapSqlParameterSource("lecNos", lecNos), LOCKED);
    }

    /** 확정: 성적상태·확정자·확정일시를 함께 씁니다(ck_lecture_confirm_time·ck_lecture_confirm_emp). */
    public void confirm(int lecNo, String empNo, LocalDateTime confirmedAt) {
        jdbc.update(
                """
                UPDATE lecture
                   SET grd_stat = 'CONFIRMED', cnfm_emp_no = :empNo, cnfm_dtm = :cnfmDtm
                 WHERE lec_no = :lecNo
                """,
                new MapSqlParameterSource("lecNo", lecNo)
                        .addValue("empNo", empNo)
                        .addValue("cnfmDtm", confirmedAt));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
