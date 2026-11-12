package com.som.connection;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 연동 데이터 수신 설정 (som.signals). ingest-enabled=false면 수신 API를 등록하지 않는다(운영 프로필, 명세 §5).
 * max-entries-per-request: 요청당 엔트리 상한. 누락 시 기동 실패.
 */
@ConfigurationProperties(prefix = "som.signals")
public record SignalProperties(Boolean ingestEnabled, Integer maxEntriesPerRequest) {
    public SignalProperties {
        Objects.requireNonNull(ingestEnabled, "som.signals.ingest-enabled must be configured");
        Objects.requireNonNull(maxEntriesPerRequest, "som.signals.max-entries-per-request must be configured");
    }
}
