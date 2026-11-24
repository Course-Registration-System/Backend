package com.crs.backend.support;

import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 시드 강의를 과목코드·학기·분반으로 찾습니다. 시드를 다시 만들면 강의번호가 바뀌므로, SeedFixtures 에 없는 강의는 번호를 적지 않고
 * 이걸로 찾습니다.
 */
public final class SeedLectures {

    private SeedLectures() {}

    /** 재학 중인 학부생 중 주어진 과목을 한 번도 신청한 적 없는 학생. 재수강·같은 과목 검사에 걸리지 않게 할 때 씁니다. */
    public static String studentNeverTook(JdbcTemplate jdbc, String... crsCds) {
        String in = String.join(",", Collections.nCopies(crsCds.length, "?"));
        return jdbc.queryForObject("""
                SELECT s.std_no FROM student s
                 WHERE s.std_stat = 'ATTENDING'
                   AND CHAR_LENGTH(s.std_no) <> 10
                   AND NOT EXISTS (SELECT 1 FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                                    WHERE e.std_no = s.std_no AND l.crs_cd IN (%s))
                 ORDER BY s.std_no
                 LIMIT 1
                """.formatted(in), String.class, (Object[]) crsCds);
    }

    public static int of(JdbcTemplate jdbc, String crsCd, int semNo, int sectNo) {
        return jdbc.queryForObject(
                "SELECT lec_no FROM lecture WHERE crs_cd = ? AND sem_no = ? AND sect_no = ?",
                Integer.class,
                crsCd,
                semNo,
                sectNo);
    }
}
