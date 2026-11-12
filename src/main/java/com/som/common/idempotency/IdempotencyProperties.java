package com.som.common.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retention 키 보존 기간. 지난 키는 새 요청으로 처리한다(정리 작업은 아직 없음).
 */
@ConfigurationProperties(prefix = "som.idempotency")
public record IdempotencyProperties(Duration retention) {
    public IdempotencyProperties {
        if (retention == null) {
            throw new IllegalStateException("som.idempotency.retention is required");
        }
    }
}
