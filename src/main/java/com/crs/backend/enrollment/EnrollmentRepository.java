package com.crs.backend.enrollment;

import com.crs.backend.enrollment.dto.EnrollmentResponse;
import com.crs.backend.lecture.LecStat;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import com.crs.backend.user.StdStat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 수강신청·취소(REG-03)의 SQL. 강의·과목·학기·선수과목·학생은 읽기만 하고, 강의의 신청인원만 바꿉니다. */
@Repository
public class EnrollmentRepository {

    public record StudentState(StdStat stdStat, int yrLvl) {}

    /** 신청할 강의. 정원 비교를 위해 잠근 채로 읽습니다. */
    public record LectureInfo(
            int semNo,
            String crsCd,
            LecStat lecStat,
            int cap,
            int enrlCnt,
            BigDecimal crdt,
            boolean chapel,
            boolean passFail) {}

    public record Prerequisite(String crsCd, String crsNm, String minGrade, boolean passFail) {}

    private final JdbcTemplate jdbc;

    public EnrollmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 잠금 (순서: 학생 → 강의)

    /** 학생 행을 잠급니다. 한 학생의 신청이 동시에 들어와도 시간 중복·학점 검사가 엇갈리지 않습니다. */
    public Optional<StudentState> lockStudent(String stdNo) {
        return jdbc
                .query(
                        "SELECT std_stat, yr_lvl FROM student WHERE std_no = ? FOR UPDATE",
                        (rs, rowNum) ->
                                new StudentState(StdStat.valueOf(rs.getString("std_stat")), rs.getInt("yr_lvl")),
                        stdNo)
                .stream()
                .findFirst();
    }

    /** 강의 행만 잠급니다(과목 행은 잠그지 않음). 여러 학생이 동시에 신청해도 정원을 넘지 않습니다. */
    public Optional<LectureInfo> lockLecture(int lecNo) {
        return jdbc
                .query(
                        """
                        SELECT l.sem_no, l.crs_cd, l.lec_stat, l.cap, l.enrl_cnt, c.crdt, c.cmpl_type, c.eval_type
                          FROM lecture l
                          JOIN course c ON c.crs_cd = l.crs_cd
                         WHERE l.lec_no = ?
                           FOR UPDATE OF l
                        """,
                        (rs, rowNum) -> new LectureInfo(
                                rs.getInt("sem_no"),
                                rs.getString("crs_cd"),
                                LecStat.valueOf(rs.getString("lec_stat")),
                                rs.getInt("cap"),
                                rs.getInt("enrl_cnt"),
                                rs.getBigDecimal("crdt"),
                                "CHAPEL".equals(rs.getString("cmpl_type")),
                                "PASS_FAIL".equals(rs.getString("eval_type"))),
                        lecNo)
                .stream()
                .findFirst();
    }

    // ---- 신청 조건

    public boolean isSeasonal(int semNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT sem_type IN ('SUMMER', 'WINTER') FROM semester WHERE sem_no = ?", Boolean.class, semNo));
    }

    public boolean enrolledInCourse(String stdNo, int semNo, String crsCd) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                                WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ? AND l.crs_cd = ?)
                """, stdNo, semNo, crsCd);
    }

    public boolean retakenCourse(String stdNo, String crsCd) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                                WHERE e.std_no = ? AND e.retake_yn = 'Y' AND e.enrl_stat = 'ENROLLED' AND l.crs_cd = ?)
                """, stdNo, crsCd);
    }

    /** 재수강한 과목 수. 같은 과목을 여러 번 재수강해도 1과목입니다. */
    public int countRetakenCourses(String stdNo) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.crs_cd) FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                 WHERE e.std_no = ? AND e.retake_yn = 'Y' AND e.enrl_stat = 'ENROLLED'
                """, Integer.class, stdNo);
    }

    /** 이미 신청한 강의 중 이 강의와 같은 요일에 시간이 겹치는 강의. 예: "198번 컴파일러" */
    public Optional<String> findTimeConflict(String stdNo, int semNo, int lecNo) {
        return jdbc.queryForList("""
                        SELECT CONCAT(l.lec_no, '번 ', c.crs_nm)
                          FROM enrollment e
                          JOIN lecture l ON l.lec_no = e.lec_no
                          JOIN course c ON c.crs_cd = l.crs_cd
                          JOIN lecture_time_slot b ON b.lec_no = l.lec_no
                          JOIN lecture_time_slot a ON a.lec_no = ? AND a.dow = b.dow
                                                  AND a.start_tm < b.end_tm AND b.start_tm < a.end_tm
                         WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ?
                         LIMIT 1
                        """, String.class, lecNo, stdNo, semNo).stream()
                .findFirst();
    }

    /** 이번 학기 신청 학점 합. 채플은 넣지 않습니다. */
    public BigDecimal sumCredits(String stdNo, int semNo) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.crdt), 0)
                  FROM enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ? AND c.cmpl_type <> 'CHAPEL'
                """, BigDecimal.class, stdNo, semNo);
    }

    public List<Prerequisite> findPrerequisites(String crsCd) {
        return jdbc.query(
                """
                SELECT p.prereq_crs_cd, c.crs_nm, p.min_req_grd, c.eval_type
                  FROM prerequisite_course p
                  JOIN course c ON c.crs_cd = p.prereq_crs_cd
                 WHERE p.crs_cd = ?
                """,
                (rs, rowNum) -> new Prerequisite(
                        rs.getString("prereq_crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getString("min_req_grd"),
                        "PASS_FAIL".equals(rs.getString("eval_type"))),
                crsCd);
    }

    // ---- 내역

    public List<EnrollmentResponse> findEnrolled(String stdNo, int semNo) {
        Map<Integer, List<TimeSlotResponse>> slots =
                TimeSlots.load(jdbc, """
                enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN lecture_time_slot t ON t.lec_no = e.lec_no
                """, "e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ?", stdNo, semNo);
        return jdbc.query(
                """
                SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, c.crdt, c.cmpl_type, u.usr_nm AS prof_nm,
                       e.retake_yn, e.enrl_dtm
                  FROM enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ?
                 ORDER BY e.enrl_dtm, l.lec_no
                """,
                (rs, rowNum) -> new EnrollmentResponse(
                        rs.getInt("lec_no"),
                        rs.getString("crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getBigDecimal("crdt"),
                        rs.getString("cmpl_type"),
                        rs.getString("prof_nm"),
                        "Y".equals(rs.getString("retake_yn")),
                        rs.getObject("enrl_dtm", LocalDateTime.class),
                        slots.getOrDefault(rs.getInt("lec_no"), List.of())),
                stdNo,
                semNo);
    }

    /** 이 학생·강의의 신청 기록이 있으면 그 신청상태(ENROLLED, CANCELLED). */
    public Optional<String> findEnrlStat(String stdNo, int lecNo) {
        return jdbc
                .queryForList(
                        "SELECT enrl_stat FROM enrollment WHERE std_no = ? AND lec_no = ?", String.class, stdNo, lecNo)
                .stream()
                .findFirst();
    }

    // ---- 쓰기

    public void insert(String stdNo, int lecNo, boolean retake, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO enrollment (std_no, lec_no, enrl_stat, retake_yn, proc_type, enrl_dtm)
                VALUES (?, ?, 'ENROLLED', ?, 'STUDENT', ?)
                """, stdNo, lecNo, yn(retake), now);
    }

    /** 취소한 기록을 되살립니다. 신청일시는 다시 신청한 시각, 취소일시는 비우고, 처리구분은 이번 처리 주체로 바꿉니다. */
    public void revive(String stdNo, int lecNo, boolean retake, LocalDateTime now) {
        jdbc.update("""
                UPDATE enrollment
                   SET enrl_stat = 'ENROLLED', retake_yn = ?, proc_type = 'STUDENT', enrl_dtm = ?, cncl_dtm = NULL
                 WHERE std_no = ? AND lec_no = ?
                """, yn(retake), now, stdNo, lecNo);
    }

    /** 신청 중인 건을 취소합니다. 취소한 건수를 돌려줍니다. */
    public int cancel(String stdNo, int lecNo, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = ?, proc_type = 'STUDENT'
                 WHERE std_no = ? AND lec_no = ? AND enrl_stat = 'ENROLLED'
                """, now, stdNo, lecNo);
    }

    public void addEnrlCnt(int lecNo, int delta) {
        jdbc.update("UPDATE lecture SET enrl_cnt = enrl_cnt + ? WHERE lec_no = ?", delta, lecNo);
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }

    private static String yn(boolean value) {
        return value ? "Y" : "N";
    }
}
