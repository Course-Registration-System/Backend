package com.crs.backend.course;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** 과목·선수과목 SQL. department 와 lecture 는 다른 모듈 소관이라 읽기만 합니다. */
@Repository
public class CourseRepository {

    /** 목록·상세 화면용 과목 한 행. 학과명을 함께 읽습니다. */
    public record CourseRow(Course course, String deptNm) {}

    /** 선수과목 한 행과 선수과목의 이름. */
    public record PrerequisiteRow(String crsCd, String prereqCrsCd, String prereqCrsNm, String minReqGrd) {}

    private static final String COURSE_COLUMNS =
            "c.crs_cd, c.dept_no, c.crs_nm, c.crdt, c.cmpl_type, c.eval_type, c.rcmd_yr_lvl";

    private static final RowMapper<Course> COURSE = (rs, rowNum) -> new Course(
            rs.getString("crs_cd"),
            rs.getInt("dept_no"),
            rs.getString("crs_nm"),
            rs.getBigDecimal("crdt"),
            CmplType.valueOf(rs.getString("cmpl_type")),
            EvalType.valueOf(rs.getString("eval_type")),
            rs.getObject("rcmd_yr_lvl", Integer.class));

    private static final RowMapper<CourseRow> COURSE_ROW =
            (rs, rowNum) -> new CourseRow(COURSE.mapRow(rs, rowNum), rs.getString("dept_nm"));

    private static final RowMapper<PrerequisiteRow> PREREQUISITE_ROW = (rs, rowNum) -> new PrerequisiteRow(
            rs.getString("crs_cd"),
            rs.getString("prereq_crs_cd"),
            rs.getString("prereq_crs_nm"),
            rs.getString("min_req_grd"));

    private final NamedParameterJdbcTemplate jdbc;

    public CourseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 조건에 맞는 과목을 과목코드 순으로. 조건이 null 이면 거르지 않습니다. keyword 는 과목명이나 과목코드의 일부입니다. */
    public List<CourseRow> findRows(Integer deptNo, CmplType cmplType, String keyword) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (deptNo != null) {
            where.append(" AND c.dept_no = :deptNo");
            params.addValue("deptNo", deptNo);
        }
        if (cmplType != null) {
            where.append(" AND c.cmpl_type = :cmplType");
            params.addValue("cmplType", cmplType.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (c.crs_nm LIKE :keyword OR c.crs_cd LIKE :keyword)");
            params.addValue("keyword", "%" + escapeLike(keyword.strip()) + "%");
        }
        return jdbc.query(selectRows(where.toString()) + " ORDER BY c.crs_cd", params, COURSE_ROW);
    }

    public Optional<CourseRow> findRow(String crsCd) {
        return jdbc
                .query(selectRows(" AND c.crs_cd = :crsCd"), new MapSqlParameterSource("crsCd", crsCd), COURSE_ROW)
                .stream()
                .findFirst();
    }

    /**
     * 과목 행을 잠그고 읽습니다. 강의를 개설(INSERT INTO lecture)할 때 외래키 검사가 이 행에 공유 잠금을 걸기 때문에, 잠근 동안에는 이 과목으로
     * 강의가 새로 개설되지 않습니다.
     */
    public Optional<Course> findForUpdate(String crsCd) {
        return jdbc
                .query(
                        "SELECT " + COURSE_COLUMNS + " FROM course c WHERE c.crs_cd = :crsCd FOR UPDATE",
                        new MapSqlParameterSource("crsCd", crsCd),
                        COURSE)
                .stream()
                .findFirst();
    }

    public boolean exists(String crsCd) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM course WHERE crs_cd = :crsCd)",
                new MapSqlParameterSource("crsCd", crsCd),
                Boolean.class));
    }

    /** 주어진 과목코드 중 실제로 있는 것. */
    public Set<String> findExistingCodes(Collection<String> crsCds) {
        if (crsCds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT crs_cd FROM course WHERE crs_cd IN (:crsCds)",
                new MapSqlParameterSource("crsCds", crsCds),
                String.class));
    }

    public boolean departmentExists(int deptNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM department WHERE dept_no = :deptNo)",
                new MapSqlParameterSource("deptNo", deptNo),
                Boolean.class));
    }

    /** 이 과목으로 개설된 강의가 있는지. 강의상태(폐강 포함)·학기와 관계없이 봅니다. lecture 는 강의 개설 모듈 소관이라 읽기만 합니다. */
    public boolean hasLecture(String crsCd) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lecture WHERE crs_cd = :crsCd)",
                new MapSqlParameterSource("crsCd", crsCd),
                Boolean.class));
    }

    public void insert(Course course) {
        jdbc.update("""
                INSERT INTO course (crs_cd, dept_no, crs_nm, crdt, cmpl_type, eval_type, rcmd_yr_lvl)
                VALUES (:crsCd, :deptNo, :crsNm, :crdt, :cmplType, :evalType, :rcmdYrLvl)
                """, params(course));
    }

    /** 과목코드를 뺀 나머지를 바꿉니다. */
    public void update(Course course) {
        jdbc.update("""
                UPDATE course
                   SET dept_no = :deptNo, crs_nm = :crsNm, crdt = :crdt, cmpl_type = :cmplType,
                       eval_type = :evalType, rcmd_yr_lvl = :rcmdYrLvl
                 WHERE crs_cd = :crsCd
                """, params(course));
    }

    /** 주어진 과목들의 선수과목을 과목코드, 선수과목코드 순으로. */
    public List<PrerequisiteRow> findPrerequisiteRows(Collection<String> crsCds) {
        if (crsCds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT p.crs_cd, p.prereq_crs_cd, c.crs_nm AS prereq_crs_nm, p.min_req_grd
                  FROM prerequisite_course p
                  JOIN course c ON c.crs_cd = p.prereq_crs_cd
                 WHERE p.crs_cd IN (:crsCds)
                 ORDER BY p.crs_cd, p.prereq_crs_cd
                """, new MapSqlParameterSource("crsCds", crsCds), PREREQUISITE_ROW);
    }

    /**
     * 모든 선수과목 관계를 잠그고 최신 값으로 읽습니다(잠금 읽기라 트랜잭션 스냅샷이 아닌 커밋된 최신 행을 읽음). 다른 요청이 같은 메서드를 부르면 이
     * 트랜잭션이 끝날 때까지 기다리므로, 선수과목 변경이 한 번에 하나씩 처리됩니다. 테이블이 완전히 비어 있으면 잠글 행이 없어, 동시에 처음 넣는 두
     * 요청 중 하나는 교착 상태 오류로 실패합니다(잘못 저장되지는 않음).
     */
    public List<PrerequisiteCourse> lockAllPrerequisites() {
        return jdbc.query(
                "SELECT crs_cd, prereq_crs_cd, min_req_grd FROM prerequisite_course FOR UPDATE",
                (rs, rowNum) -> new PrerequisiteCourse(
                        rs.getString("crs_cd"), rs.getString("prereq_crs_cd"), rs.getString("min_req_grd")));
    }

    public void deletePrerequisites(String crsCd) {
        jdbc.update("DELETE FROM prerequisite_course WHERE crs_cd = :crsCd", new MapSqlParameterSource("crsCd", crsCd));
    }

    public void insertPrerequisites(List<PrerequisiteCourse> prerequisites) {
        if (prerequisites.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = prerequisites.stream()
                .map(p -> new MapSqlParameterSource()
                        .addValue("crsCd", p.crsCd())
                        .addValue("prereqCrsCd", p.prereqCrsCd())
                        .addValue("minReqGrd", p.minReqGrd()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO prerequisite_course (crs_cd, prereq_crs_cd, min_req_grd)
                VALUES (:crsCd, :prereqCrsCd, :minReqGrd)
                """, batch);
    }

    private static String selectRows(String where) {
        return "SELECT " + COURSE_COLUMNS + ", d.dept_nm"
                + " FROM course c JOIN department d ON d.dept_no = c.dept_no"
                + " WHERE 1 = 1" + where;
    }

    private static MapSqlParameterSource params(Course course) {
        return new MapSqlParameterSource()
                .addValue("crsCd", course.crsCd())
                .addValue("deptNo", course.deptNo())
                .addValue("crsNm", course.crsNm())
                .addValue("crdt", course.crdt())
                .addValue("cmplType", course.cmplType().name())
                .addValue("evalType", course.evalType().name())
                .addValue("rcmdYrLvl", course.rcmdYrLvl());
    }

    /** LIKE 의 특수문자(%, _, \)를 글자 그대로 찾도록 바꿉니다. MySQL 의 기본 이스케이프 문자는 \ 입니다. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
