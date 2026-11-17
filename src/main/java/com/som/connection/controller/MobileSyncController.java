package com.som.connection.controller;

import com.som.auth.web.CurrentUserId;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import com.som.connection.dto.SignalIngestRequest;
import com.som.connection.dto.SignalIngestResponse;
import com.som.connection.service.SignalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile-sync")
public class MobileSyncController {

    private final SignalService signals;
    private final RateLimiter limiter;
    private final RateLimitProperties.Signals limits;

    public MobileSyncController(SignalService signals, RateLimiter limiter, RateLimitProperties limits) {
        this.signals = signals;
        this.limiter = limiter;
        this.limits = limits.signals();
    }

    /** 서비스 날짜의 전체 걸음 수를 교체한다. 동기화 재시도는 값을 누적하지 않는다. */
    @PutMapping("/steps")
    SignalIngestResponse steps(@CurrentUserId Long userId, @Valid @RequestBody Steps body) {
        return sync(userId, new SignalIngestRequest(DataSource.STEPS, body.localDate(),
                List.of(entry(Metric.STEPS_DAILY, body.total()))));
    }

    /** 일정 원문 대신 서비스 날짜별 집계값만 수신한다. */
    @PutMapping("/calendar/{calendar}")
    SignalIngestResponse calendar(@CurrentUserId Long userId, @PathVariable CalendarType calendar,
                                  @Valid @RequestBody CalendarDay body) {
        List<SignalIngestRequest.Entry> entries = new ArrayList<>(List.of(
                entry(Metric.CALENDAR_EVENT_COUNT, body.eventCount()),
                entry(Metric.CALENDAR_BUSY_MINUTES, body.busyMinutes())));
        if (body.averageGapMinutes() != null) {
            entries.add(entry(Metric.CALENDAR_AVERAGE_GAP_MINUTES, body.averageGapMinutes()));
        }
        return sync(userId, new SignalIngestRequest(calendar.source, body.localDate(), entries));
    }

    private SignalIngestResponse sync(Long userId, SignalIngestRequest request) {
        RateLimiter.Decision decision = limiter.tryConsume("signals:user:" + userId, limits.perUser());
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
        return signals.ingestMobile(userId, request);
    }

    private static SignalIngestRequest.Entry entry(Metric metric, int total) {
        return new SignalIngestRequest.Entry(metric, BigDecimal.valueOf(total), null, null);
    }

    enum CalendarType {
        personal(DataSource.CALENDAR_PERSONAL), work(DataSource.CALENDAR_WORK);

        private final DataSource source;

        CalendarType(DataSource source) {
            this.source = source;
        }
    }

    record CalendarDay(@NotNull LocalDate localDate, @NotNull @PositiveOrZero Integer eventCount,
                       @NotNull @PositiveOrZero @Max(1440) Integer busyMinutes,
                       @PositiveOrZero @Max(1440) Integer averageGapMinutes) {}

    record Steps(@NotNull LocalDate localDate, @NotNull @PositiveOrZero Integer total) {}
}
