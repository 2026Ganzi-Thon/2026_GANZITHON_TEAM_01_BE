package com.som.connection.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarControllerTest {

    @Test
    void mergesOverlapsAndClipsIntervalsToTheServiceDate() throws Exception {
        var busy = new ObjectMapper().readTree("""
                [
                  {"start":"2026-11-18T09:30:00+09:00","end":"2026-11-18T11:00:00+09:00"},
                  {"start":"2026-11-18T00:00:00Z","end":"2026-11-18T01:00:00Z"},
                  {"start":"2026-11-17T23:30:00+09:00","end":"2026-11-18T00:30:00+09:00"},
                  {"start":"2026-11-18T23:45:00+09:00","end":"2026-11-19T03:00:00+09:00"}
                ]
                """);
        var start = OffsetDateTime.parse("2026-11-18T00:00:00+09:00");
        assertThat(GoogleCalendarController.busyMinutes(busy, start, start.plusDays(1))).isEqualTo(165);
    }
}
