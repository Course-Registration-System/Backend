package com.crs.backend.common.demo;

import com.crs.backend.common.ClockConfig;
import com.crs.backend.common.SettableClock;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 시연 전용 기능: 앱 시계 옮기기와 시드 오버레이 적용. demo 프로필에서만 등록됩니다. */
@Profile("demo")
@Service
public class DemoService {

    /** 적용 순서대로. 각 오버레이는 한 번씩만 적용할 수 있습니다. */
    static final List<String> SCENARIOS = List.of("s1_grade_close", "s2_enroll_day", "s3_adjust");

    private final SettableClock clock;
    private final DataSource dataSource;

    public DemoService(SettableClock clock, DataSource dataSource) {
        this.clock = clock;
        this.dataSource = dataSource;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 이 시각에 시계를 멈춥니다. null 이면 실제 시각으로 돌아갑니다. */
    public LocalDateTime moveClock(LocalDateTime at) {
        clock.fixAt(at == null ? null : at.atZone(ClockConfig.ZONE).toInstant());
        return now();
    }

    @Transactional
    public void applyScenario(String name) {
        if (!SCENARIOS.contains(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "오버레이 이름: " + SCENARIOS);
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/scenario/" + name + ".sql"));
        } catch (ScriptStatementFailedException e) {
            // 같은 오버레이를 두 번 적용했거나 앞 단계를 건너뛴 경우. 트랜잭션이 롤백돼 데이터는 그대로다
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 적용했거나 앞 단계를 먼저 적용해야 합니다: " + name, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
