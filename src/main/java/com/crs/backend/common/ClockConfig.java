package com.crs.backend.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱의 현재 시각. 현재 시각이 필요한 코드는 {@code java.time.Clock} 을 주입받아 {@code LocalDateTime.now(clock)} 처럼
 * 씁니다.
 *
 * <p>{@code crs.clock.fixed-at} 에 시각을 넣으면 그 시각에 멈춘 시계를, 비우면 실제 시계를 씁니다. 시드 데이터는 특정 시점을
 * 기준으로 만들어서 개발·시연 중에는 시각을 고정합니다. demo 프로필에서는 시연 API 로 시각을 옮길 수 있습니다
 * (docs/seed-data.md).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public SettableClock clock(@Value("${crs.clock.fixed-at:}") String fixedAt) {
        if (fixedAt == null || fixedAt.isBlank()) {
            return new SettableClock(ZONE, null);
        }
        return new SettableClock(ZONE, LocalDateTime.parse(fixedAt).atZone(ZONE).toInstant());
    }
}
