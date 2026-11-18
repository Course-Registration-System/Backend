package com.crs.backend.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** 멈춘 시각을 바꿀 수 있는 시계. 멈춘 시각이 없으면 실제 시각을 돌려줍니다. 시각 변경은 시연 API 에서만 합니다. */
public class SettableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> fixed;

    public SettableClock(ZoneId zone, Instant fixedOrNull) {
        this(zone, new AtomicReference<>(fixedOrNull));
    }

    private SettableClock(ZoneId zone, AtomicReference<Instant> fixed) {
        this.zone = zone;
        this.fixed = fixed;
    }

    /** 이 시각에 멈춥니다. null 이면 실제 시각으로 돌아갑니다. */
    public void fixAt(Instant instant) {
        fixed.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return new SettableClock(other, fixed);
    }

    @Override
    public Instant instant() {
        Instant at = fixed.get();
        return at != null ? at : Instant.now();
    }
}
