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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    record Steps(@NotNull LocalDate localDate, @NotNull @PositiveOrZero Integer total) {}
}
