package com.crs.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 시연용 SQL(demo/*.sql)이 적힌 대로 동작하는지 확인합니다. 파일 첫 줄의 "-- 계정: X" 로 접속하고, 문장마다 위의
 * "-- 기대: 성공" 또는 "-- 기대: 거부 오류문구" 와 결과를 비교합니다. 실행한 변경은 모두 롤백합니다.
 */
class DemoScriptTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "01_state_change.sql,01_state_change_undo.sql",
                "02_constraint_violations.sql",
                "03_permission_app.sql",
                "04_permission_readonly.sql"
            })
    void 시연_SQL이_기대대로_동작한다(String files) throws IOException, SQLException {
        List<String> failures = new ArrayList<>();
        String account = account(files.split(",")[0]);
        try (Connection connection = dataSourceAs(account).getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String file : files.split(",")) {
                    run(connection, file, failures);
                }
            } finally {
                connection.rollback();
            }
        }
        assertThat(failures).isEmpty();
    }

    private static String account(String file) throws IOException {
        String first = Files.readAllLines(Path.of("demo", file), StandardCharsets.UTF_8)
                .getFirst();
        return first.replace("-- 계정:", "").trim();
    }

    private static void run(Connection connection, String file, List<String> failures) throws IOException {
        String expect = null;
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(Path.of("demo", file), StandardCharsets.UTF_8)) {
            if (line.startsWith("-- 기대:")) {
                expect = line.substring("-- 기대:".length()).trim();
                continue;
            }
            if (expect == null || line.startsWith("--") || line.isBlank()) {
                continue;
            }
            sql.append(line).append('\n');
            if (line.stripTrailing().endsWith(";")) {
                String statement = sql.toString().trim();
                String problem = check(connection, statement.substring(0, statement.length() - 1), expect);
                if (problem != null) {
                    failures.add(file + ": " + problem + " ← " + statement);
                }
                sql.setLength(0);
                expect = null;
            }
        }
    }

    /** 기대와 다르면 이유를, 같으면 null 을 돌려줍니다. */
    private static String check(Connection connection, String sql, String expect) {
        try (Statement statement = connection.createStatement()) {
            boolean isQuery = statement.execute(sql);
            if (expect.startsWith("거부")) {
                return "거부돼야 하는데 성공함";
            }
            if (!isQuery && statement.getUpdateCount() < 1) {
                return "바뀐 행이 없음";
            }
            return null;
        } catch (SQLException e) {
            if (expect.startsWith("거부")
                    && e.getMessage().contains(expect.substring("거부".length()).trim())) {
                return null;
            }
            return "예상과 다른 결과: " + e.getMessage();
        }
    }
}
