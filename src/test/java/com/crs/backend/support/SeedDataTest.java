package com.crs.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 데이터가 DB 제약으로 막지 못하는 업무 규칙(정원, 시간 겹침, 최대학점, 재수강, 상대평가, 학년·졸업 등)을 시점마다 지키는지
 * seed-checks.sql 로 확인합니다. 시드를 고치면 이 테스트가 먼저 알려줍니다.
 */
@Transactional
class SeedDataTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void T0_기본_시드가_업무_규칙을_지킨다() throws IOException {
        assertThat(violations("T0", SeedTime.T0)).isEmpty();
    }

    @Test
    void T1_신청일_데이터가_업무_규칙을_지킨다() throws IOException {
        SeedTime.atT1(dataSource);
        assertThat(violations("T1", SeedTime.T1)).isEmpty();
        Integer max = jdbc.queryForObject(
                "SELECT max_crdt FROM semester_maximum_credit WHERE std_no = ? AND sem_no = ?",
                Integer.class,
                SeedFixtures.Student.DEMO,
                SeedFixtures.Semester.Y2027_FIRST);
        assertThat(max).isEqualTo(22);
    }

    @Test
    void T2_정정_기간_데이터가_업무_규칙을_지킨다() throws IOException {
        SeedTime.atT2(dataSource);
        assertThat(violations("T2", SeedTime.T2)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT sem_no FROM semester WHERE cur_sem_yn = 'Y'", Integer.class))
                .isEqualTo(SeedFixtures.Semester.Y2027_FIRST);
    }

    /** 검사마다 "이름: 실제값 (기대값)" 을 돌려줍니다. 비어 있으면 모두 통과입니다. */
    private List<String> violations(String stage, String now) throws IOException {
        jdbc.execute("SET @now = '" + now.replace('T', ' ') + "'");
        String script = new ClassPathResource("seed/seed-checks.sql").getContentAsString(StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        String[] blocks = script.split("(?m)^-- CHECK ");
        for (int i = 1; i < blocks.length; i++) { // 0번은 파일 머리 주석
            String block = blocks[i];
            String header = block.substring(0, block.indexOf('\n'));
            String name = header.substring(0, header.lastIndexOf('|')).trim();
            Long expected =
                    expected(header.substring(header.lastIndexOf('|') + 1).trim(), stage);
            if (expected == null) {
                continue;
            }
            String sql = block.substring(block.indexOf('\n') + 1).trim();
            Long actual = jdbc.queryForObject(sql.substring(0, sql.lastIndexOf(';')), Long.class);
            if (actual == null || !actual.equals(expected)) {
                failures.add(name + ": " + actual + " (기대 " + expected + ")");
            }
        }
        return failures;
    }

    /** "0" 은 모든 시점, "T0:0, T1:1" 은 적힌 시점만. 해당 없으면 null. */
    private static Long expected(String spec, String stage) {
        if (!spec.contains(":")) {
            return Long.parseLong(spec);
        }
        for (String part : spec.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv[0].equals(stage)) {
                return Long.parseLong(kv[1]);
            }
        }
        return null;
    }
}
