package com.som.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트용 시계. 테스트마다 set()으로 시각을 바꾸고 @AfterEach에서 reset()한다. */
public class MutableClock extends Clock {

    public static final Instant DEFAULT = Instant.parse("2026-11-11T05:00:00Z"); // KST 2026-11-11(수) 14:00
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private volatile Instant instant = DEFAULT;

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void set(String isoInstant) {
        set(Instant.parse(isoInstant));
    }

    public void reset() {
        this.instant = DEFAULT;
    }

    @Override
    public ZoneId getZone() {
        return SEOUL;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
