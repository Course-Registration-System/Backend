package com.crs.backend.user;

import com.crs.backend.common.auth.Role;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private static final RowMapper<UserAccount> ROW = (rs, rowNum) -> new UserAccount(
            rs.getString("usr_no"),
            rs.getString("pwd"),
            rs.getString("usr_nm"),
            rs.getString("email"),
            Role.valueOf(rs.getString("role")),
            AcctStat.valueOf(rs.getString("acct_stat")));

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByUsrNo(String usrNo) {
        return jdbc
                .query(
                        "SELECT usr_no, pwd, usr_nm, email, role, acct_stat FROM user_account WHERE usr_no = ?",
                        ROW,
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
}
