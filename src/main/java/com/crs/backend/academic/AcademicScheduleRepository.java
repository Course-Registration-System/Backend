package com.crs.backend.academic;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 학사일정(academic_schedule)·학기·학기별최대학점의 조회 SQL. 기간 판정에 쓰는 읽기만 있습니다. */
@Repository
public class AcademicScheduleRepository {

    private final JdbcTemplate jdbc;

    public AcademicScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 장바구니·수강신청·정정 일정 중 끝나지 않은 것이 있는 학기 가운데, 그 일정이 가장 먼저 시작하는 학기. */
    public Optional<Integer> findRegistrationSemester(LocalDateTime now) {
        return jdbc.queryForList("""
                        SELECT sem_no
                          FROM academic_schedule
                         WHERE schd_type IN ('CART', 'ENROLL', 'ADJUST')
                         GROUP BY sem_no
                        HAVING MAX(end_dtm) > ?
                         ORDER BY MIN(start_dtm), sem_no
                         LIMIT 1
                        """, Integer.class, now).stream().findFirst();
    }

    /** 현재 학기. 없으면 비어 있습니다. */
    public Optional<Integer> findCurrentSemester() {
        return jdbc.queryForList("SELECT sem_no FROM semester WHERE cur_sem_yn = 'Y'", Integer.class).stream()
                .findFirst();
    }

    /** 지금 열려 있는 장바구니 일정이 있는지. 시작 시각은 포함, 종료 시각은 제외합니다. */
    public boolean isCartOpen(int semNo, LocalDateTime now) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM academic_schedule
                                WHERE sem_no = ? AND schd_type = 'CART' AND start_dtm <= ? AND ? < end_dtm)
                """, semNo, now, now);
    }

    /**
     * 지금 그 학년이 쓸 수 있는 수강신청일(본인 학년 또는 전체) 또는 정정 일정이 열려 있는지. 대상학년은 수강신청 일정에만 있고 정정은 늘 전
     * 학년이지만, 조건은 두 유형에 같이 걸어 둡니다.
     */
    public boolean isEnrollOpen(int semNo, int yrLvl, LocalDateTime now) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM academic_schedule
                                WHERE sem_no = ? AND schd_type IN ('ENROLL', 'ADJUST')
                                  AND (tgt_yr_lvl = ? OR tgt_yr_lvl IS NULL)
                                  AND start_dtm <= ? AND ? < end_dtm)
                """, semNo, yrLvl, now, now);
    }

    public Optional<Integer> findMaxCredit(String stdNo, int semNo) {
        return jdbc
                .queryForList(
                        "SELECT max_crdt FROM semester_maximum_credit WHERE std_no = ? AND sem_no = ?",
                        Integer.class,
                        stdNo,
                        semNo)
                .stream()
                .findFirst();
    }

    /** 계절학기(여름·겨울)인지. 없는 학기면 false. */
    public boolean isSeasonal(int semNo) {
        return exists(
                "SELECT EXISTS (SELECT 1 FROM semester WHERE sem_no = ? AND sem_type IN ('SUMMER', 'WINTER'))", semNo);
    }

    public boolean isOnLeave(String stdNo) {
        return exists("SELECT EXISTS (SELECT 1 FROM student WHERE std_no = ? AND std_stat = 'ON_LEAVE')", stdNo);
    }

    /** 장바구니 시작. 장바구니 일정이 없으면 수강신청 시작으로 대신합니다. */
    public Optional<LocalDateTime> findCartStart(int semNo) {
        return queryTime(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('CART', 'ENROLL')",
                semNo);
    }

    public Optional<LocalDateTime> findEnrollStart(int semNo) {
        return queryTime(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ENROLL'", semNo);
    }

    public Optional<LocalDateTime> findAdjustStart(int semNo) {
        return queryTime(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'", semNo);
    }

    public Optional<LocalDateTime> findAdjustEnd(int semNo) {
        return queryTime("SELECT MAX(end_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'", semNo);
    }

    public Optional<LocalDateTime> findRegistrationEnd(int semNo) {
        return queryTime(
                "SELECT MAX(end_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('ENROLL', 'ADJUST')",
                semNo);
    }

    /** 지금 열려 있는 성적입력 일정이 있는지. 시작 시각은 포함, 종료 시각은 제외합니다. */
    public boolean isGradeOpen(int semNo, LocalDateTime now) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM academic_schedule
                                WHERE sem_no = ? AND schd_type = 'GRADE' AND start_dtm <= ? AND ? < end_dtm)
                """, semNo, now, now);
    }

    public Optional<LocalDateTime> findGradeEnd(int semNo) {
        return queryTime("SELECT MAX(end_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'GRADE'", semNo);
    }

    private Optional<LocalDateTime> queryTime(String sql, int semNo) {
        return Optional.ofNullable(jdbc.queryForObject(sql, LocalDateTime.class, semNo));
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
