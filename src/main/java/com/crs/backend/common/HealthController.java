package com.crs.backend.common;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서버가 떠 있는지 확인하는 용도. 프론트 프록시 연결 테스트에도 씁니다. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", OffsetDateTime.now().toString());
    }
}
