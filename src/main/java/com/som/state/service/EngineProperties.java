package com.som.state.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * engine 호출 상한(som.engine). 같은 접두사의 mode·mock 키는 다른 클래스가 읽는다.
 *
 * @param timeout     호출 1회 타임아웃
 * @param maxAttempts 최초 호출 포함 최대 시도 횟수
 * @param poolSize    engine 호출 전용 스레드 수
 */
@ConfigurationProperties(prefix = "som.engine")
public record EngineProperties(Duration timeout, int maxAttempts, int poolSize) {
    public EngineProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || maxAttempts < 1 || poolSize < 1) {
            throw new IllegalStateException("som.engine.timeout, max-attempts and pool-size must be positive");
        }
    }
}
