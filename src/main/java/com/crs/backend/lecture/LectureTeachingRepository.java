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
 *
 * <p>요일·수업유형·강의상태는 강의 개설 PR(#10)의 enum 이 main 에 들어오기 전까지 문자열로 다룹니다.
 */
@Repository
public class LectureTeachingRepository {

    /** 학기 한 행. */
    public record SemesterRow(int semNo, int yr, String semType) {}

    /** 담당 강의 한 행과 수강 인원. */
    public record TeachingLectureRow(
            int lecNo, String crsCd, String crsNm, int sectNo, int cap, int enrlCnt, String lecStat) {}

    /** 강의시간 한 칸과 강의실명. roomCd·roomNm 은 온라인이거나 미배정이면 null 입니다. */
    public record TimeSlotRow(
            int lecNo, String dow, LocalTime startTm, LocalTime endTm, String clsType, String roomCd, String roomNm) {}

    private static final RowMapper<SemesterRow> SEMESTER =
            (rs, rowNum) -> new SemesterRow(rs.getInt("sem_no"), rs.getInt("yr"), rs.getString("sem_type"));

    private static final RowMapper<TeachingLectureRow> LECTURE = (rs, rowNum) -> new TeachingLectureRow(
            rs.getInt("lec_no"),
            rs.getString("crs_cd"),
            rs.getString("crs_nm"),
            rs.getInt("sect_no"),
            rs.getInt("cap"),
            rs.getInt("enrl_cnt"),
            rs.getString("lec_stat"));

    private static final RowMapper<TimeSlotRow> TIME_SLOT = (rs, rowNum) -> new TimeSlotRow(
            rs.getInt("lec_no"),
            rs.getString("dow"),
            rs.getObject("start_tm", LocalTime.class),
            rs.getObject("end_tm", LocalTime.class),
            rs.getString("cls_type"),
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

    /** 그 학기에 stdNo 가 조교로 배정된 강의. 폐강 포함, 과목코드·분반 순. */
    public List<TeachingLectureRow> findByAssistant(int semNo, String stdNo) {
        return jdbc.query(
                LECTURE_SELECT
                        + " JOIN teaching_assistant_assignment a ON a.lec_no = l.lec_no"
                        + " WHERE l.sem_no = :semNo AND a.std_no = :stdNo ORDER BY l.crs_cd, l.sect_no",
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

    public boolean isAssistantOf(int lecNo, String stdNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assistant_assignment WHERE lec_no = :lecNo AND std_no = :stdNo)",
                new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("stdNo", stdNo),
                Boolean.class));
    }
}
