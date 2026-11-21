package com.crs.backend.common.demo;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/clock")
    public Map<String, Object> now() {
        return Map.of("now", demoService.now().toString());
    }

    @PutMapping("/clock")
    public Map<String, Object> moveClock(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return Map.of("now", demoService.moveClock(at).toString());
    }

    @PostMapping("/scenarios/{name}")
    public Map<String, Object> applyScenario(@PathVariable String name) {
        demoService.applyScenario(name);
        return Map.of("applied", name);
    }
}
