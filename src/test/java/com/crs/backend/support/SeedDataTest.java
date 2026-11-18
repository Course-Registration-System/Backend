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
 * 시드 데이터가 DB 제약으로 막지 못하는 업무 규칙(정원, 시간 겹침, 최대학점, 재수강, 상대평가 등)을 지키는지
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
        assertThat(violations(SeedTime.T0)).isEmpty();
    }

    @Test
    void T3_오버레이를_적용해도_업무_규칙을_지킨다() throws IOException {
        SeedTime.applyT3(dataSource);
        assertThat(violations(SeedTime.T3)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT sem_no FROM semester WHERE cur_sem_yn = 'Y'", Integer.class))
                .isEqualTo(SeedFixtures.Semester.Y2027_FIRST);
    }

    /** 검사마다 "이름: 실제값 (기대값)" 을 돌려줍니다. 비어 있으면 모두 통과입니다. */
    private List<String> violations(String now) throws IOException {
        jdbc.execute("SET @now = '" + now.replace('T', ' ') + "'");
        String script = new ClassPathResource("seed/seed-checks.sql").getContentAsString(StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        String[] blocks = script.split("(?m)^-- CHECK ");
        for (int i = 1; i < blocks.length; i++) { // 0번은 파일 머리 주석
            String block = blocks[i];
            String header = block.substring(0, block.indexOf('\n'));
            String name = header.substring(0, header.lastIndexOf('|')).trim();
            long expected =
                    Long.parseLong(header.substring(header.lastIndexOf('|') + 1).trim());
            String sql = block.substring(block.indexOf('\n') + 1).trim();
            Long actual = jdbc.queryForObject(sql.substring(0, sql.lastIndexOf(';')), Long.class);
            if (actual == null || actual != expected) {
                failures.add(name + ": " + actual + " (기대 " + expected + ")");
            }
        }
        return failures;
    }
}
