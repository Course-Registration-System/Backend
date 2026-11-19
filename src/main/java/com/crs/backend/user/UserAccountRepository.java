package com.crs.backend.user;

import com.crs.backend.common.auth.Role;
import com.crs.backend.user.dto.UserResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** user_account 와 서브타입 student·professor 의 SQL. 관리자 계정은 시드로만 만들고 여기서 쓰지 않습니다. */
@Repository
public class UserAccountRepository {

    private static final RowMapper<UserAccount> ACCOUNT_ROW = (rs, rowNum) -> new UserAccount(
            rs.getString("usr_no"),
            rs.getString("pwd"),
            rs.getString("usr_nm"),
            rs.getString("email"),
            Role.valueOf(rs.getString("role")),
            AcctStat.valueOf(rs.getString("acct_stat")));

    private static final RowMapper<UserResponse> USER_ROW = (rs, rowNum) -> {
        String stdStat = rs.getString("std_stat");
        return new UserResponse(
                rs.getString("usr_no"),
                rs.getString("usr_nm"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                AcctStat.valueOf(rs.getString("acct_stat")),
                rs.getInt("dept_no"),
                rs.getString("dept_nm"),
                rs.getObject("yr_lvl", Integer.class),
                stdStat == null ? null : StdStat.valueOf(stdStat),
                rs.getString("office"));
    };

    // 학생과 교수를 한 목록으로 보여 주려고 두 서브타입을 LEFT JOIN 한다.
    // 관리자는 서브타입 행이 없어 학과 JOIN 에서 빠지므로 목록과 조회에 나오지 않는다
    private static final String USER_SELECT = """
            SELECT u.usr_no, u.usr_nm, u.email, u.role, u.acct_stat,
                   d.dept_no, d.dept_nm, s.yr_lvl, s.std_stat, p.office
              FROM user_account u
              LEFT JOIN student s ON s.std_no = u.usr_no
              LEFT JOIN professor p ON p.prof_no = u.usr_no
              JOIN department d ON d.dept_no = COALESCE(s.dept_no, p.dept_no)
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public UserAccountRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public Optional<UserAccount> findByUsrNo(String usrNo) {
        return jdbc
                .query(
                        "SELECT usr_no, pwd, usr_nm, email, role, acct_stat FROM user_account WHERE usr_no = ?",
                        ACCOUNT_ROW,
                        usrNo)
                .stream()
                .findFirst();
    }

    /** 조교로 배정된 강의가 하나라도 있으면 true. 조교배정 테이블은 강의 운영(LEC-04) 소관이라 읽기만 합니다. */
    public boolean isTeachingAssistant(String stdNo) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assistant_assignment WHERE std_no = ?)", Boolean.class, stdNo);
        return Boolean.TRUE.equals(exists);
    }

    /** 학생·교수 목록. role 과 keyword(로그인ID 또는 이름 일부)는 비우면 조건에서 빠집니다. */
    public List<UserResponse> search(Role role, String keyword) {
        var params = new MapSqlParameterSource()
                .addValue("role", role == null ? null : role.name())
                .addValue("keyword", keyword == null ? null : "%" + keyword + "%");
        String sql = USER_SELECT + """
                 WHERE (:role IS NULL OR u.role = :role)
                   AND (:keyword IS NULL OR u.usr_no LIKE :keyword OR u.usr_nm LIKE :keyword)
                 ORDER BY u.role, u.usr_no
                """;
        return namedJdbc.query(sql, params, USER_ROW);
    }

    public Optional<UserResponse> findUser(String usrNo) {
        return jdbc.query(USER_SELECT + " WHERE u.usr_no = ?", USER_ROW, usrNo).stream()
                .findFirst();
    }

    public boolean existsByUsrNo(String usrNo) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_account WHERE usr_no = ?)", Boolean.class, usrNo);
        return Boolean.TRUE.equals(exists);
    }

    /** 학과는 시드로만 넣는 테이블이라 읽기만 합니다. */
    public boolean departmentExists(int deptNo) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM department WHERE dept_no = ?)", Boolean.class, deptNo);
        return Boolean.TRUE.equals(exists);
    }

    public void insertAccount(String usrNo, String pwdHash, String usrNm, String email, Role role) {
        jdbc.update(
                "INSERT INTO user_account (usr_no, pwd, usr_nm, email, role) VALUES (?, ?, ?, ?, ?)",
                usrNo,
                pwdHash,
                usrNm,
                email,
                role.name());
    }

    public void insertStudent(String stdNo, int deptNo, int yrLvl, StdStat stdStat) {
        jdbc.update(
                "INSERT INTO student (std_no, dept_no, yr_lvl, std_stat) VALUES (?, ?, ?, ?)",
                stdNo,
                deptNo,
                yrLvl,
                stdStat.name());
    }

    public void insertProfessor(String profNo, int deptNo, String office) {
        jdbc.update("INSERT INTO professor (prof_no, dept_no, office) VALUES (?, ?, ?)", profNo, deptNo, office);
    }

    public void updateAccount(String usrNo, String usrNm, String email) {
        jdbc.update("UPDATE user_account SET usr_nm = ?, email = ? WHERE usr_no = ?", usrNm, email, usrNo);
    }

    public void updateStudent(String stdNo, int deptNo, int yrLvl, StdStat stdStat) {
        jdbc.update(
                "UPDATE student SET dept_no = ?, yr_lvl = ?, std_stat = ? WHERE std_no = ?",
                deptNo,
                yrLvl,
                stdStat.name(),
                stdNo);
    }

    public void updateProfessor(String profNo, int deptNo, String office) {
        jdbc.update("UPDATE professor SET dept_no = ?, office = ? WHERE prof_no = ?", deptNo, office, profNo);
    }

    public void deactivate(String usrNo) {
        jdbc.update("UPDATE user_account SET acct_stat = 'INACTIVE' WHERE usr_no = ?", usrNo);
    }
}
