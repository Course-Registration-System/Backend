package com.crs.backend.academic;

import com.crs.backend.academic.dto.ScheduleResponse;
import com.crs.backend.academic.dto.SemesterResponse;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** semester, academic_schedule 쓰기와 관리 화면용 조회(ACA-01). 기간 판정은 SchedulePolicy 쪽에서 합니다. */
@Repository
public class SemesterRepository {

    private static final RowMapper<SemesterResponse> SEMESTER_ROW = (rs, rowNum) -> {
        SemType semType = SemType.valueOf(rs.getString("sem_type"));
        return new SemesterResponse(
                rs.getInt("sem_no"),
                rs.getInt("yr"),
                semType,
                semType.regular(),
                "Y".equals(rs.getString("cur_sem_yn")));
    };

    private static final RowMapper<ScheduleResponse> SCHEDULE_ROW = (rs, rowNum) -> new ScheduleResponse(
            rs.getInt("schd_no"),
            rs.getInt("sem_no"),
            SchdType.valueOf(rs.getString("schd_type")),
            rs.getObject("tgt_yr_lvl", Integer.class),
            rs.getTimestamp("start_dtm").toLocalDateTime(),
            rs.getTimestamp("end_dtm").toLocalDateTime());

    private static final String SEMESTER_SELECT = "SELECT sem_no, yr, sem_type, cur_sem_yn FROM semester";

    private static final String SCHEDULE_SELECT =
            "SELECT schd_no, sem_no, schd_type, tgt_yr_lvl, start_dtm, end_dtm FROM academic_schedule";

    private final JdbcTemplate jdbc;

    public SemesterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 한 해 안의 순서는 1학기 → 여름 → 2학기 → 겨울입니다. */
    public List<SemesterResponse> findAll() {
        return jdbc.query(
                SEMESTER_SELECT + " ORDER BY yr, FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER')", SEMESTER_ROW);
    }

    public Optional<SemesterResponse> findById(int semNo) {
        return jdbc.query(SEMESTER_SELECT + " WHERE sem_no = ?", SEMESTER_ROW, semNo).stream()
                .findFirst();
    }

    /** 학기 행을 잠급니다. 두 관리자가 동시에 넣은 일정이 서로의 겹침 검사를 피하지 못하게 일정 등록·삭제 전에 부릅니다. */
    public boolean lock(int semNo) {
        return !jdbc.queryForList("SELECT sem_no FROM semester WHERE sem_no = ? FOR UPDATE", Integer.class, semNo)
                .isEmpty();
    }

    public boolean exists(int yr, SemType semType, int exceptSemNo) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM semester WHERE yr = ? AND sem_type = ? AND sem_no <> ?",
                Integer.class,
                yr,
                semType.name(),
                exceptSemNo);
        return count != null && count > 0;
    }

    public int insert(int yr, SemType semType) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO semester (yr, sem_type) VALUES (?, ?)", new String[] {"sem_no"});
                    ps.setInt(1, yr);
                    ps.setString(2, semType.name());
                    return ps;
                },
                key);
        return key.getKey().intValue();
    }

    public void update(int semNo, int yr, SemType semType) {
        jdbc.update("UPDATE semester SET yr = ?, sem_type = ? WHERE sem_no = ?", yr, semType.name(), semNo);
    }

    /**
     * 현재 학기를 한 문장으로 바꿉니다. 현재 학기는 식 기반 UNIQUE 로 하나만 허용되고 MySQL 은 UNIQUE 를 행마다 바로 검사하므로, 기존 현재
     * 학기를 먼저 N 으로 바꾸도록 ORDER BY 로 순서를 정합니다. 순서가 없으면 번호가 작은 학기로 바꿀 때 새 학기가 먼저 Y 가 되어 실패합니다.
     */
    public void changeCurrent(int semNo) {
        jdbc.update(
                "UPDATE semester SET cur_sem_yn = IF(sem_no = ?, 'Y', 'N') WHERE cur_sem_yn = 'Y' OR sem_no = ?"
                        + " ORDER BY cur_sem_yn = 'Y' DESC",
                semNo,
                semNo);
    }

    public boolean anyScheduleStarted(int semNo, LocalDateTime now) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic_schedule WHERE sem_no = ? AND start_dtm <= ?",
                Integer.class,
                semNo,
                Timestamp.valueOf(now));
        return count != null && count > 0;
    }

    public List<ScheduleResponse> findSchedules(int semNo) {
        return jdbc.query(
                SCHEDULE_SELECT
                        + " WHERE sem_no = ?"
                        + " ORDER BY start_dtm, FIELD(schd_type, 'CART', 'ENROLL', 'ADJUST', 'GRADE'), tgt_yr_lvl DESC",
                SCHEDULE_ROW,
                semNo);
    }

    public Optional<ScheduleResponse> findSchedule(int schdNo) {
        return jdbc.query(SCHEDULE_SELECT + " WHERE schd_no = ?", SCHEDULE_ROW, schdNo).stream()
                .findFirst();
    }

    /** 대상학년이 맞물리는 같은 학기 일정. 전 학년(NULL) 일정은 모든 학년과 맞물립니다. */
    public List<ScheduleResponse> findSchedulesSharingYear(int semNo, Integer tgtYrLvl) {
        return jdbc.query(
                SCHEDULE_SELECT + " WHERE sem_no = ? AND (? IS NULL OR tgt_yr_lvl IS NULL OR tgt_yr_lvl = ?)",
                SCHEDULE_ROW,
                semNo,
                tgtYrLvl,
                tgtYrLvl);
    }

    /**
     * 계절학기 바로 다음 학기가 정규학기면 그 학기의 장바구니 시작. 장바구니 일정이 없으면 수강신청 시작으로 대신합니다. 다음 학기가 없거나
     * 일정이 없으면 비어 있습니다.
     */
    public Optional<LocalDateTime> findNextRegularCartStart(int targetOrd) {
        return queryTime("""
                SELECT MIN(a.start_dtm) FROM academic_schedule a
                 WHERE a.schd_type IN ('CART', 'ENROLL')
                   AND a.sem_no = (SELECT IF(s.sem_type IN ('FIRST', 'SECOND'), s.sem_no, NULL) FROM semester s
                                    WHERE %s > ? ORDER BY %s LIMIT 1)
                """.formatted(MaxCreditRepository.SEM_ORD, MaxCreditRepository.SEM_ORD), targetOrd);
    }

    /** 정규학기 바로 앞 학기가 계절학기면 그 학기의 성적입력 종료. 앞 학기가 없거나 일정이 없으면 비어 있습니다. */
    public Optional<LocalDateTime> findPreviousSeasonalGradeEnd(int targetOrd) {
        return queryTime("""
                SELECT MAX(a.end_dtm) FROM academic_schedule a
                 WHERE a.schd_type = 'GRADE'
                   AND a.sem_no = (SELECT IF(s.sem_type IN ('SUMMER', 'WINTER'), s.sem_no, NULL) FROM semester s
                                    WHERE %s < ? ORDER BY %s DESC LIMIT 1)
                """.formatted(MaxCreditRepository.SEM_ORD, MaxCreditRepository.SEM_ORD), targetOrd);
    }

    private Optional<LocalDateTime> queryTime(String sql, int targetOrd) {
        return Optional.ofNullable(jdbc.queryForObject(sql, LocalDateTime.class, targetOrd));
    }

    public int insertSchedule(
            int semNo, SchdType schdType, Integer tgtYrLvl, LocalDateTime startDtm, LocalDateTime endDtm) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO academic_schedule (sem_no, schd_type, tgt_yr_lvl, start_dtm, end_dtm)"
                                    + " VALUES (?, ?, ?, ?, ?)",
                            new String[] {"schd_no"});
                    ps.setInt(1, semNo);
                    ps.setString(2, schdType.name());
                    ps.setObject(3, tgtYrLvl);
                    ps.setTimestamp(4, Timestamp.valueOf(startDtm));
                    ps.setTimestamp(5, Timestamp.valueOf(endDtm));
                    return ps;
                },
                key);
        return key.getKey().intValue();
    }

    public void deleteSchedule(int schdNo) {
        jdbc.update("DELETE FROM academic_schedule WHERE schd_no = ?", schdNo);
    }
}
