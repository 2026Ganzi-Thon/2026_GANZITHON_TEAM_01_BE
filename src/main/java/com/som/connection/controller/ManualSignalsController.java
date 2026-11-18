package com.som.connection.controller;

import com.som.auth.web.CurrentUserId;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import com.som.connection.domain.DataSource;
import com.som.connection.dto.SignalIngestResponse;
import com.som.connection.service.SignalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/manual-signals/{date}")
public class ManualSignalsController {

    private final SignalService signals;
    private final RateLimiter limiter;
    private final RateLimitProperties.Signals limits;

    public ManualSignalsController(SignalService signals, RateLimiter limiter, RateLimitProperties limits) {
        this.signals = signals;
        this.limiter = limiter;
        this.limits = limits.signals();
    }

    @GetMapping
    ManualTotals get(@CurrentUserId Long userId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new ManualTotals(signals.manualTotal(userId, date, DataSource.STEPS),
                signals.manualTotal(userId, date, DataSource.SLEEP));
    }

    @PutMapping("/steps")
    SignalIngestResponse steps(@CurrentUserId Long userId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @Valid @RequestBody Steps body) {
        return save(userId, date, DataSource.STEPS, body.total());
    }

    @PutMapping("/sleep")
    SignalIngestResponse sleep(@CurrentUserId Long userId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @Valid @RequestBody Sleep body) {
        return save(userId, date, DataSource.SLEEP, body.total());
    }

    private SignalIngestResponse save(Long userId, LocalDate date, DataSource source, int total) {
        var decision = limiter.tryConsume("signals:user:" + userId, limits.perUser());
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
        return signals.recordManual(userId, date, source, total);
    }

    record ManualTotals(Integer steps, Integer sleepMinutes) {}
    record Steps(@NotNull @PositiveOrZero Integer total) {}
    record Sleep(@NotNull @PositiveOrZero @Max(1440) Integer total) {}
}
