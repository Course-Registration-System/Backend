package com.crs.backend.common.demo;

import com.crs.backend.common.ClockConfig;
import com.crs.backend.common.SettableClock;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 시연·개발 전용 API. demo 프로필에서만 등록되고 운영에는 없습니다.
 *
 * <ul>
 *   <li>{@code GET /api/dev/clock}: 앱의 현재 시각
 *   <li>{@code PUT /api/dev/clock?at=2027-02-11T10:05:00}: 시각을 옮김(at 을 빼면 실제 시각)
 *   <li>{@code POST /api/dev/scenarios/s1_grade_close}: 시드 오버레이 적용(s1 → s2 → s3 순서로 한 번씩)
 * </ul>
 */
@Profile("demo")
@RestController
@RequestMapping("/api/dev")
public class DemoController {

    private static final List<String> SCENARIOS = List.of("s1_grade_close", "s2_enroll_day", "s3_adjust");

    private final SettableClock clock;
    private final DataSource dataSource;

    public DemoController(SettableClock clock, DataSource dataSource) {
        this.clock = clock;
        this.dataSource = dataSource;
    }

    @GetMapping("/clock")
    public Map<String, Object> now() {
        return Map.of("now", LocalDateTime.now(clock).toString());
    }

    @PutMapping("/clock")
    public Map<String, Object> moveClock(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        clock.fixAt(at == null ? null : at.atZone(ClockConfig.ZONE).toInstant());
        return now();
    }

    @PostMapping("/scenarios/{name}")
    @Transactional
    public Map<String, Object> applyScenario(@PathVariable String name) {
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
        return Map.of("applied", name);
    }
}
