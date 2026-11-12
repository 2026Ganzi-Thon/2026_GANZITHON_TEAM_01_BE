package com.som.connection;

import com.som.connection.domain.DataSource;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소스별 항목별 동의 시트 고지 내용 (som.consent.sources). 동의 이력에는 클라이언트 값이 아니라 이 값만 기록한다.
 * 미정 값(보관 기간 등)은 application.yml의 TODO(DECISION)로 관리한다.
 */
@ConfigurationProperties(prefix = "som.consent")
public record SourceConsentProperties(Map<DataSource, Notice> sources) {
    public record Notice(String policyVersion, List<String> items, String purpose, String retention,
                         Boolean sensitive) {}
}
