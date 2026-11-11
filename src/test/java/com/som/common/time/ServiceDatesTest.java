package com.som.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceDatesTest {

    // 서비스 시간대는 설정(yml)에서만 정한다. 단위 테스트는 같은 오프셋(+09:00)의 고정 시계를 쓴다.
    private static final ZoneId KST = ZoneOffset.ofHours(9);

    private static ServiceDates at(String instant) {
        return new ServiceDates(Clock.fixed(Instant.parse(instant), KST));
    }

    @Test
    void today_KST_자정이면_다음날() {
        assertThat(at("2026-11-11T15:00:00Z").today()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void today_KST_자정_직전이면_당일() {
        assertThat(at("2026-11-11T14:59:59Z").today()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void currentWeek_일요일이면_그주_월요일부터_일요일() {
        // 2026-11-15(일) KST 12:00
        DateRange week = at("2026-11-15T03:00:00Z").currentWeek();
        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void currentWeek_월요일_자정이면_그날이_주의_시작() {
        // 2026-11-09(월) KST 00:00
        DateRange week = at("2026-11-08T15:00:00Z").currentWeek();
        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(week.days()).hasSize(7);
    }

    @Test
    void recentDays_7일은_오늘_포함_7개() {
        ServiceDates dates = at("2026-11-12T03:00:00Z");
        List<LocalDate> days = dates.recentDays(7).days();
        assertThat(days).hasSize(7);
        assertThat(days.get(0)).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(days.get(6)).isEqualTo(dates.today());
        assertThat(days).isSorted();
    }

    @Test
    void recentDays_30일은_30개() {
        ServiceDates dates = at("2026-11-12T03:00:00Z");
        List<LocalDate> days = dates.recentDays(30).days();
        assertThat(days).hasSize(30);
        assertThat(days.get(29)).isEqualTo(dates.today());
    }

    @Test
    void recentDays_1미만이면_예외() {
        ServiceDates dates = at("2026-11-12T03:00:00Z");
        assertThatThrownBy(() -> dates.recentDays(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dates.recentDays(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void now_오프셋은_플러스9() {
        assertThat(at("2026-11-12T03:00:00Z").now().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    void toLocalDate_서비스_시간대_기준() {
        ServiceDates dates = at("2026-11-12T03:00:00Z");
        assertThat(dates.toLocalDate(Instant.parse("2026-11-11T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(dates.toLocalDate(Instant.parse("2026-11-11T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 11));
    }
}
