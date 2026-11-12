package com.som.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

/** 서비스 시간대(Clock.getZone()) 기준 날짜 계산. */
@Component
public class ServiceDates {

    private final Clock clock;

    public ServiceDates(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** DB에서 읽은 시각을 서비스 시간대 오프셋(+09:00)으로 바꾼다. null은 null. */
    public OffsetDateTime inServiceZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(clock.getZone()).toOffsetDateTime();
    }

    public LocalDate toLocalDate(Instant instant) {
        return LocalDate.ofInstant(instant, clock.getZone());
    }

    /** 오늘이 속한 주 월요일 ~ 일요일. */
    public DateRange currentWeek() {
        LocalDate today = today();
        return new DateRange(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    }

    /** 오늘 포함 최근 N일 (days=7 → 오늘-6 ~ 오늘). */
    public DateRange recentDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1: " + days);
        }
        LocalDate today = today();
        return new DateRange(today.minusDays(days - 1L), today);
    }
}
