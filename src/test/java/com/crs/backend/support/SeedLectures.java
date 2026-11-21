package com.crs.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 시드 강의를 과목코드·학기·분반으로 찾습니다. 시드를 다시 만들면 강의번호가 바뀌므로, SeedFixtures 에 없는 강의는 번호를 적지 않고
 * 이걸로 찾습니다.
 */
public final class SeedLectures {

    private SeedLectures() {}

    public static int of(JdbcTemplate jdbc, String crsCd, int semNo, int sectNo) {
        return jdbc.queryForObject(
                "SELECT lec_no FROM lecture WHERE crs_cd = ? AND sem_no = ? AND sect_no = ?",
                Integer.class,
                crsCd,
                semNo,
                sectNo);
    }
}
