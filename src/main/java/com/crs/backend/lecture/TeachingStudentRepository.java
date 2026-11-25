package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.TeachingStudentItem;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 수강생 명단 조회(LEC-02) SQL. 모두 읽기만 합니다. lecture·enrollment·student·user_account 는 강의 개설·수강신청·계정 모듈,
 * department 는 학사 모듈 소관입니다.
 */
@Repository
public class TeachingStudentRepository {

    private static final RowMapper<TeachingStudentItem> STUDENT = (rs, rowNum) -> new TeachingStudentItem(
            rs.getString("std_no"),
            rs.getString("usr_nm"),
            rs.getString("dept_nm"),
            rs.getInt("yr_lvl"),
            rs.getString("email"));

    private final NamedParameterJdbcTemplate jdbc;

    public TeachingStudentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 강의의 학기번호. 없는 강의면 비어 있습니다. */
    public Optional<Integer> findSemNo(int lecNo) {
        return jdbc
                .queryForList(
                        "SELECT sem_no FROM lecture WHERE lec_no = :lecNo",
                        new MapSqlParameterSource("lecNo", lecNo),
                        Integer.class)
                .stream()
                .findFirst();
    }

    /** 신청상태가 '신청'인 수강생. 취소한 학생은 빠지고, 학번 순입니다. */
    public List<TeachingStudentItem> findEnrolled(int lecNo) {
        return jdbc.query("""
                SELECT e.std_no, u.usr_nm, d.dept_nm, s.yr_lvl, u.email
                  FROM enrollment e
                  JOIN student s ON s.std_no = e.std_no
                  JOIN user_account u ON u.usr_no = e.std_no
                  JOIN department d ON d.dept_no = s.dept_no
                 WHERE e.lec_no = :lecNo
                   AND e.enrl_stat = 'ENROLLED'
                 ORDER BY e.std_no
                """, new MapSqlParameterSource("lecNo", lecNo), STUDENT);
    }
}
