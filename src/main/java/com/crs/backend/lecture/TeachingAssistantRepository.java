package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.TeachingAssistantItem;
import com.crs.backend.user.AcctStat;
import com.crs.backend.user.StdStat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 조교 관리(LEC-04) SQL. teaching_assistant_assignment 만 쓰고, lecture·student·user_account·department 는 다른
 * 모듈 소관이라 읽기만 합니다.
 */
@Repository
public class TeachingAssistantRepository {

    /** 강의의 담당 교수, 강의상태, 성적 확정 여부(성적상태 CONFIRMED). */
    public record LectureOwnerRow(String profNo, LecStat lecStat, boolean gradeConfirmed) {}

    /** 조교 후보 학생의 계정상태와 학적상태. */
    public record StudentStatusRow(AcctStat acctStat, StdStat stdStat) {}

    private final NamedParameterJdbcTemplate jdbc;

    public TeachingAssistantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LectureOwnerRow> findLectureOwner(int lecNo) {
        return jdbc
                .query(
                        "SELECT prof_no, lec_stat, grd_stat FROM lecture WHERE lec_no = :lecNo",
                        new MapSqlParameterSource("lecNo", lecNo),
                        (rs, rowNum) -> new LectureOwnerRow(
                                rs.getString("prof_no"),
                                LecStat.valueOf(rs.getString("lec_stat")),
                                "CONFIRMED".equals(rs.getString("grd_stat"))))
                .stream()
                .findFirst();
    }

    /** 역할이 학생인 사용자만 찾습니다. 교수·관리자 번호나 없는 번호면 비어 있습니다. */
    public Optional<StudentStatusRow> findStudentStatus(String stdNo) {
        return jdbc
                .query(
                        """
                        SELECT u.acct_stat, s.std_stat
                          FROM student s
                          JOIN user_account u ON u.usr_no = s.std_no
                         WHERE s.std_no = :stdNo AND u.role = 'STUDENT'
                        """,
                        new MapSqlParameterSource("stdNo", stdNo),
                        (rs, rowNum) -> new StudentStatusRow(
                                AcctStat.valueOf(rs.getString("acct_stat")), StdStat.valueOf(rs.getString("std_stat"))))
                .stream()
                .findFirst();
    }

    public boolean exists(int lecNo, String stdNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assistant_assignment WHERE lec_no = :lecNo AND std_no = :stdNo)",
                params(lecNo, stdNo),
                Boolean.class));
    }

    /** 강의의 조교 목록. 학번 순. 졸업생 조교도 배정 기록이 있으면 보입니다(권한은 LectureAccess 가 따로 판단). */
    public List<TeachingAssistantItem> findAssistants(int lecNo) {
        return jdbc.query(
                """
                SELECT a.std_no, u.usr_nm, d.dept_nm, s.yr_lvl, u.email
                  FROM teaching_assistant_assignment a
                  JOIN student s ON s.std_no = a.std_no
                  JOIN user_account u ON u.usr_no = a.std_no
                  JOIN department d ON d.dept_no = s.dept_no
                 WHERE a.lec_no = :lecNo
                 ORDER BY a.std_no
                """,
                new MapSqlParameterSource("lecNo", lecNo),
                (rs, rowNum) -> new TeachingAssistantItem(
                        rs.getString("std_no"),
                        rs.getString("usr_nm"),
                        rs.getString("dept_nm"),
                        rs.getInt("yr_lvl"),
                        rs.getString("email")));
    }

    public void insert(TeachingAssistantAssignment assignment) {
        jdbc.update(
                "INSERT INTO teaching_assistant_assignment (lec_no, std_no) VALUES (:lecNo, :stdNo)",
                params(assignment.lecNo(), assignment.stdNo()));
    }

    /** 지운 행 수. 배정되지 않았으면 0 입니다. */
    public int delete(int lecNo, String stdNo) {
        return jdbc.update(
                "DELETE FROM teaching_assistant_assignment WHERE lec_no = :lecNo AND std_no = :stdNo",
                params(lecNo, stdNo));
    }

    private static MapSqlParameterSource params(int lecNo, String stdNo) {
        return new MapSqlParameterSource().addValue("lecNo", lecNo).addValue("stdNo", stdNo);
    }
}
