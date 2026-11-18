package com.crs.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway 가 스키마와 권한을 적용했고, 앱 계정이 설계한 권한만 갖는지 확인합니다. */
class DatabaseSetupTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 스키마의_테이블이_모두_만들어진다() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'crs' AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tables).isEqualTo(17);
    }

    @Test
    void 앱_계정은_조회할_수_있다() {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class);
        assertThat(users).isNotNull();
    }

    @Test
    void 앱_계정은_이력_테이블을_지울_수_없다() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM enrollment"))
                .rootCause()
                .hasMessageContaining("DELETE command denied");
    }

    @Test
    void 앱_계정은_테이블_구조를_바꿀_수_없다() {
        assertThatThrownBy(() -> jdbc.execute("DROP TABLE course_cart"))
                .rootCause()
                .hasMessageContaining("DROP command denied");
    }
}
