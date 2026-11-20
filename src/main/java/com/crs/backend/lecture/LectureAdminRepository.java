package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.LectureResponse;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 강의 개설 관리(ACA-03)의 SQL. 과목·학기·교수·강의실·학사일정은 다른 업무의 테이블이라 읽기만 합니다.
 *
 * <p>폐강할 때 함께 정리하는 수강신청·장바구니도 같은 모듈(B)의 테이블이라 여기서 씁니다.
 */
@Repository
public class LectureAdminRepository {

    private static final String LECTURE_SELECT = """
            SELECT l.lec_no, l.crs_cd, c.crs_nm, c.crdt, l.sem_no, l.prof_no, u.usr_nm AS prof_nm,
                   l.sect_no, l.cap, l.enrl_cnt, l.lec_stat, l.eng_lec_yn, l.rel_grd_exmp_yn
              FROM lecture l
              JOIN course c ON c.crs_cd = l.crs_cd
              JOIN user_account u ON u.usr_no = l.prof_no
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public LectureAdminRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    // ---- 조회

    public List<LectureResponse> findBySemester(int semNo) {
        Map<Integer, List<TimeSlotResponse>> slots = findSlots("l.sem_no = ?", semNo);
        return jdbc.query(
                LECTURE_SELECT + " WHERE l.sem_no = ? ORDER BY l.crs_cd, l.sect_no",
                (rs, rowNum) -> toLecture(rs, slots),
                semNo);
    }

    public Optional<LectureResponse> findById(int lecNo) {
        Map<Integer, List<TimeSlotResponse>> slots = findSlots("l.lec_no = ?", lecNo);
        return jdbc.query(LECTURE_SELECT + " WHERE l.lec_no = ?", (rs, rowNum) -> toLecture(rs, slots), lecNo).stream()
                .findFirst();
    }

    /** 강의번호별 강의시간. 조건(condition)은 이 클래스 안의 고정 문자열만 넘깁니다. */
    private Map<Integer, List<TimeSlotResponse>> findSlots(String condition, int value) {
        String sql = """
                SELECT t.lec_no, t.dow, t.start_tm, t.end_tm, t.cls_type, t.room_cd, r.room_nm
                  FROM lecture_time_slot t
                  JOIN lecture l ON l.lec_no = t.lec_no
                  LEFT JOIN classroom r ON r.room_cd = t.room_cd
                 WHERE %s
                 ORDER BY FIELD(t.dow, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'), t.start_tm
                """.formatted(condition);
        Map<Integer, List<TimeSlotResponse>> slots = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    slots.computeIfAbsent(rs.getInt("lec_no"), key -> new ArrayList<>())
                            .add(new TimeSlotResponse(
                                    Dow.valueOf(rs.getString("dow")),
                                    rs.getObject("start_tm", LocalTime.class),
                                    rs.getObject("end_tm", LocalTime.class),
                                    ClsType.valueOf(rs.getString("cls_type")),
                                    rs.getString("room_cd"),
                                    rs.getString("room_nm")));
                },
                value);
        return slots;
    }

    private LectureResponse toLecture(ResultSet rs, Map<Integer, List<TimeSlotResponse>> slots) throws SQLException {
        int lecNo = rs.getInt("lec_no");
        return new LectureResponse(
                lecNo,
                rs.getString("crs_cd"),
                rs.getString("crs_nm"),
                rs.getBigDecimal("crdt"),
                rs.getInt("sem_no"),
                rs.getString("prof_no"),
                rs.getString("prof_nm"),
                rs.getInt("sect_no"),
                rs.getInt("cap"),
                rs.getInt("enrl_cnt"),
                LecStat.valueOf(rs.getString("lec_stat")),
                "Y".equals(rs.getString("eng_lec_yn")),
                "Y".equals(rs.getString("rel_grd_exmp_yn")),
                slots.getOrDefault(lecNo, List.of()));
    }

    // ---- 존재 확인

    public boolean courseExists(String crsCd) {
        return exists("SELECT EXISTS (SELECT 1 FROM course WHERE crs_cd = ?)", crsCd);
    }

    public boolean semesterExists(int semNo) {
        return exists("SELECT EXISTS (SELECT 1 FROM semester WHERE sem_no = ?)", semNo);
    }

    /** 교수 계정이 있고 비활성(퇴직 등)이 아니어야 담당할 수 있습니다. */
    public boolean activeProfessorExists(String profNo) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM professor p JOIN user_account u ON u.usr_no = p.prof_no
                                WHERE p.prof_no = ? AND u.acct_stat = 'ACTIVE')
                """, profNo);
    }

    public boolean classroomExists(String roomCd) {
        return exists("SELECT EXISTS (SELECT 1 FROM classroom WHERE room_cd = ?)", roomCd);
    }

    public boolean sectionExists(int semNo, String crsCd, int sectNo) {
        return exists(
                "SELECT EXISTS (SELECT 1 FROM lecture WHERE sem_no = ? AND crs_cd = ? AND sect_no = ?)",
                semNo,
                crsCd,
                sectNo);
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }

    // ---- 시간 중복

    /** 같은 학기의 개설 강의 중 같은 강의실·요일에서 시간이 겹치는 강의. 예: "198번 컴파일러" */
    public Optional<String> findRoomConflict(int semNo, int exceptLecNo, LectureTimeSlot slot) {
        return findConflict("t.room_cd = ?", slot.roomCd(), semNo, exceptLecNo, slot);
    }

    /** 같은 학기의 개설 강의 중 같은 교수·요일에서 시간이 겹치는 강의. */
    public Optional<String> findProfessorConflict(int semNo, int exceptLecNo, String profNo, LectureTimeSlot slot) {
        return findConflict("l.prof_no = ?", profNo, semNo, exceptLecNo, slot);
    }

    private Optional<String> findConflict(
            String condition, String value, int semNo, int exceptLecNo, LectureTimeSlot slot) {
        // 두 구간 [시작, 종료)가 겹치는 조건: 상대 시작 < 내 종료 AND 내 시작 < 상대 종료
        String sql = """
                SELECT CONCAT(l.lec_no, '번 ', c.crs_nm)
                  FROM lecture_time_slot t
                  JOIN lecture l ON l.lec_no = t.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE l.sem_no = ? AND l.lec_stat = 'OPEN' AND l.lec_no <> ?
                   AND t.dow = ? AND t.start_tm < ? AND ? < t.end_tm
                   AND %s
                 LIMIT 1
                """.formatted(condition);
        return jdbc
                .queryForList(
                        sql, String.class, semNo, exceptLecNo, slot.dow().name(), slot.endTm(), slot.startTm(), value)
                .stream()
                .findFirst();
    }

    // ---- 잠금

    /**
     * 강의 행을 잠급니다(트랜잭션이 끝날 때까지). 수강신청도 같은 행을 먼저 잠그므로, 정원 변경·폐강이 동시에 들어온 신청과 엇갈리지
     * 않습니다. 강의가 없으면 false.
     */
    public boolean lockLecture(int lecNo) {
        return !jdbc.queryForList("SELECT lec_no FROM lecture WHERE lec_no = ? FOR UPDATE", Integer.class, lecNo)
                .isEmpty();
    }

    // ---- 학사일정 (ACA-01 소관, 읽기만)

    /** 장바구니 기간의 시작. 장바구니 일정이 없으면 수강신청 시작으로 대신합니다. */
    public Optional<LocalDateTime> findCartStart(int semNo) {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type IN ('CART', 'ENROLL')",
                LocalDateTime.class,
                semNo));
    }

    /** 학년별 수강신청일 중 가장 이른 시작. 일정이 아직 없으면 비어 있습니다. */
    public Optional<LocalDateTime> findEnrollStart(int semNo) {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT MIN(start_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ENROLL'",
                LocalDateTime.class,
                semNo));
    }

    public Optional<LocalDateTime> findAdjustEnd(int semNo) {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT MAX(end_dtm) FROM academic_schedule WHERE sem_no = ? AND schd_type = 'ADJUST'",
                LocalDateTime.class,
                semNo));
    }

    // ---- 쓰기

    public int insertLecture(
            String crsCd, int semNo, String profNo, int sectNo, int cap, boolean engLec, boolean relGrdExmp) {
        var params = new MapSqlParameterSource()
                .addValue("crsCd", crsCd)
                .addValue("semNo", semNo)
                .addValue("profNo", profNo)
                .addValue("sectNo", sectNo)
                .addValue("cap", cap)
                .addValue("engLec", yn(engLec))
                .addValue("relGrdExmp", yn(relGrdExmp));
        KeyHolder key = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO lecture (crs_cd, sem_no, prof_no, sect_no, cap, eng_lec_yn, rel_grd_exmp_yn)
                VALUES (:crsCd, :semNo, :profNo, :sectNo, :cap, :engLec, :relGrdExmp)
                """, params, key, new String[] {"lec_no"});
        return key.getKey().intValue();
    }

    public void updateLecture(int lecNo, String profNo, int cap, boolean engLec, boolean relGrdExmp) {
        jdbc.update(
                "UPDATE lecture SET prof_no = ?, cap = ?, eng_lec_yn = ?, rel_grd_exmp_yn = ? WHERE lec_no = ?",
                profNo,
                cap,
                yn(engLec),
                yn(relGrdExmp),
                lecNo);
    }

    public void replaceSlots(int lecNo, List<LectureTimeSlot> slots) {
        jdbc.update("DELETE FROM lecture_time_slot WHERE lec_no = ?", lecNo);
        for (LectureTimeSlot slot : slots) {
            jdbc.update(
                    """
                    INSERT INTO lecture_time_slot (lec_no, dow, start_tm, end_tm, cls_type, room_cd)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    lecNo,
                    slot.dow().name(),
                    slot.startTm(),
                    slot.endTm(),
                    slot.clsType().name(),
                    slot.roomCd());
        }
    }

    // ---- 폐강

    /** 신청 중인 수강신청을 모두 관리자 처리로 취소합니다. 취소한 건수를 돌려줍니다. */
    public int cancelEnrollments(int lecNo, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enrollment SET enrl_stat = 'CANCELLED', cncl_dtm = ?, proc_type = 'ADMIN'
                 WHERE lec_no = ? AND enrl_stat = 'ENROLLED'
                """, now, lecNo);
    }

    public void close(int lecNo) {
        jdbc.update("UPDATE lecture SET lec_stat = 'CLOSED', enrl_cnt = 0 WHERE lec_no = ?", lecNo);
    }

    public void deleteCarts(int lecNo) {
        jdbc.update("DELETE FROM course_cart WHERE lec_no = ?", lecNo);
    }

    private static String yn(boolean value) {
        return value ? "Y" : "N";
    }
}
