package com.crs.backend.lecture;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 담당 강의 조회(LEC-01)와 강의 관리 권한 확인 SQL. 모두 읽기만 합니다. lecture·lecture_time_slot·enrollment 는 강의 개설·수강신청 모듈,
 * semester 는 학사 모듈 소관입니다.
 */
@Repository
public class LectureTeachingRepository {

    /** 학기 한 행. */
    public record SemesterRow(int semNo, int yr, String semType) {}

    /** 담당 강의 한 행과 수강 인원. */
    public record TeachingLectureRow(
            int lecNo, String crsCd, String crsNm, int sectNo, int cap, int enrlCnt, LecStat lecStat) {}

    /** 강의시간 한 칸과 강의실명. roomCd·roomNm 은 온라인이거나 미배정이면 null 입니다. */
    public record TimeSlotRow(
            int lecNo, Dow dow, LocalTime startTm, LocalTime endTm, ClsType clsType, String roomCd, String roomNm) {}

    private static final RowMapper<SemesterRow> SEMESTER =
            (rs, rowNum) -> new SemesterRow(rs.getInt("sem_no"), rs.getInt("yr"), rs.getString("sem_type"));

    private static final RowMapper<TeachingLectureRow> LECTURE = (rs, rowNum) -> new TeachingLectureRow(
            rs.getInt("lec_no"),
            rs.getString("crs_cd"),
            rs.getString("crs_nm"),
            rs.getInt("sect_no"),
            rs.getInt("cap"),
            rs.getInt("enrl_cnt"),
            LecStat.valueOf(rs.getString("lec_stat")));

    private static final RowMapper<TimeSlotRow> TIME_SLOT = (rs, rowNum) -> new TimeSlotRow(
            rs.getInt("lec_no"),
            Dow.valueOf(rs.getString("dow")),
            rs.getObject("start_tm", LocalTime.class),
            rs.getObject("end_tm", LocalTime.class),
            ClsType.valueOf(rs.getString("cls_type")),
            rs.getString("room_cd"),
            rs.getString("room_nm"));

    // 수강 인원은 신청상태가 '신청'인 건만 센다(LEC-01)
    private static final String LECTURE_SELECT = """
            SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, l.cap, l.lec_stat,
                   (SELECT COUNT(*) FROM enrollment e WHERE e.lec_no = l.lec_no AND e.enrl_stat = 'ENROLLED') AS enrl_cnt
              FROM lecture l
              JOIN course c ON c.crs_cd = l.crs_cd
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LectureTeachingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    public Optional<SemesterRow> findSemester(int semNo) {
        return jdbc
                .query(
                        "SELECT sem_no, yr, sem_type FROM semester WHERE sem_no = :semNo",
                        new MapSqlParameterSource("semNo", semNo),
                        SEMESTER)
                .stream()
                .findFirst();
    }

    /** 그 학기에 profNo 가 담당교수인 강의. 폐강 포함, 과목코드·분반 순. */
    public List<TeachingLectureRow> findByProfessor(int semNo, String profNo) {
        return jdbc.query(
                LECTURE_SELECT + " WHERE l.sem_no = :semNo AND l.prof_no = :profNo ORDER BY l.crs_cd, l.sect_no",
                new MapSqlParameterSource().addValue("semNo", semNo).addValue("profNo", profNo),
                LECTURE);
    }

    /**
     * 조교 권한이 있는 배정(teaching_assistant_assignment a, student s)의 조건. 담당 강의 목록과 권한 확인이 같은 기준을 쓰도록 한 곳에 둡니다.
     *
     * <ul>
     *   <li>졸업생이 아니다. 졸업생은 로그인과 성적 조회만 할 수 있다(USR-02).
     *   <li>그 강의의 수강생(신청상태 '신청')이 아니다. 수강생은 조교가 될 수 없다(LEC-04, 자기 성적 처리 금지). 조교 추가 때 막아도 배정 뒤
     *       수강신청·관리자 추가로 수강생이 될 수 있어 여기서도 막는다. 수강을 취소하면 다시 권한이 생긴다.
     * </ul>
     */
    private static final String ASSISTANT_HAS_AUTHORITY = """
            s.std_stat <> 'GRADUATED'
            AND NOT EXISTS (
              SELECT 1 FROM enrollment own
               WHERE own.lec_no = a.lec_no AND own.std_no = a.std_no AND own.enrl_stat = 'ENROLLED')
            """;

    /** 그 학기에 stdNo 가 조교로 배정된 강의 중 조교 권한이 있는 것. 폐강 포함, 과목코드·분반 순. */
    public List<TeachingLectureRow> findByAssistant(int semNo, String stdNo) {
        return jdbc.query(
                LECTURE_SELECT
                        + " JOIN teaching_assistant_assignment a ON a.lec_no = l.lec_no"
                        + " JOIN student s ON s.std_no = a.std_no"
                        + " WHERE l.sem_no = :semNo AND a.std_no = :stdNo AND " + ASSISTANT_HAS_AUTHORITY
                        + " ORDER BY l.crs_cd, l.sect_no",
                new MapSqlParameterSource().addValue("semNo", semNo).addValue("stdNo", stdNo),
                LECTURE);
    }

    /** 주어진 강의들의 강의시간. 강의번호, 요일(월→일), 시작시각 순. */
    public List<TimeSlotRow> findTimeSlots(Collection<Integer> lecNos) {
        if (lecNos.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT t.lec_no, t.dow, t.start_tm, t.end_tm, t.cls_type, t.room_cd, r.room_nm
                  FROM lecture_time_slot t
                  LEFT JOIN classroom r ON r.room_cd = t.room_cd
                 WHERE t.lec_no IN (:lecNos)
                 ORDER BY t.lec_no, FIELD(t.dow, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'), t.start_tm
                """, new MapSqlParameterSource("lecNos", lecNos), TIME_SLOT);
    }

    public boolean isProfessorOf(int lecNo, String profNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lecture WHERE lec_no = :lecNo AND prof_no = :profNo)",
                new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("profNo", profNo),
                Boolean.class));
    }

    /**
     * stdNo 가 lecNo 강의의 조교 권한이 있는지. 배정돼 있어도 졸업생이거나 그 강의의 수강생이면 false 입니다({@link #ASSISTANT_HAS_AUTHORITY}).
     * student·enrollment 는 다른 모듈 소관이라 읽기만 합니다.
     */
    public boolean isAssistantOf(int lecNo, String stdNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assistant_assignment a JOIN student s ON s.std_no = a.std_no"
                        + " WHERE a.lec_no = :lecNo AND a.std_no = :stdNo AND " + ASSISTANT_HAS_AUTHORITY + ")",
                new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("stdNo", stdNo),
                Boolean.class));
    }
}
