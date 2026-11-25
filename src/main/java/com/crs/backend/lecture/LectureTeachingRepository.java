package com.crs.backend.lecture;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 담당 강의 조회(LEC-01)와 강의 관리 권한 확인 SQL. 모두 읽기만 합니다. 강의시간은 {@link LectureSearchRepository#findSlots} 를 씁니다.
 * lecture·enrollment 는 강의 개설·수강신청 모듈,
 * semester 는 학사 모듈 소관입니다.
 */
@Repository
public class LectureTeachingRepository {

    /** 학기 한 행. */
    public record SemesterRow(int semNo, int yr, String semType) {}

    /** 담당 강의 한 행과 수강 인원. */
    public record TeachingLectureRow(
            int lecNo, String crsCd, String crsNm, int sectNo, int cap, int enrlCnt, LecStat lecStat) {}

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

    // 수강 인원은 신청상태가 '신청'인 건만 센다(LEC-01). lecture.enrl_cnt 가 신청·취소·폐강 때 같이 바뀌는 그 값이라 강의 검색·장바구니와 같이 이것을 쓴다
    private static final String LECTURE_SELECT = """
            SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, l.cap, l.lec_stat, l.enrl_cnt
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
     * <p>졸업생이 아니어야 한다. 졸업생은 로그인과 성적 조회만 할 수 있다(USR-02). 조교는 대학원생만 배정되고(LEC-04, V8) 대학원생은
     * 수강신청을 할 수 없어(V6) "그 강의의 수강생은 조교 권한이 없다"는 따로 보지 않는다.
     */
    private static final String ASSISTANT_HAS_AUTHORITY = "s.std_stat <> 'GRADUATED'";

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

    public boolean isProfessorOf(int lecNo, String profNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lecture WHERE lec_no = :lecNo AND prof_no = :profNo)",
                new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("profNo", profNo),
                Boolean.class));
    }

    /**
     * stdNo 가 lecNo 강의의 조교 권한이 있는지. 배정돼 있어도 졸업생이면 false 입니다({@link #ASSISTANT_HAS_AUTHORITY}).
     * student 는 다른 모듈 소관이라 읽기만 합니다.
     */
    public boolean isAssistantOf(int lecNo, String stdNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assistant_assignment a JOIN student s ON s.std_no = a.std_no"
                        + " WHERE a.lec_no = :lecNo AND a.std_no = :stdNo AND " + ASSISTANT_HAS_AUTHORITY + ")",
                new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("stdNo", stdNo),
                Boolean.class));
    }
}
