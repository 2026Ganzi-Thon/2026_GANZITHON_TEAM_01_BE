package com.som.auth;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 필수 약관 게이트 설정 (som.consent.gate). exempt-paths: 필수 동의 전에도 허용할 Ant 패턴(계정·약관 API). 누락 시 기동 실패. */
@ConfigurationProperties(prefix = "som.consent.gate")
public record ConsentGateProperties(List<String> exemptPaths) {
    public ConsentGateProperties {
        Objects.requireNonNull(exemptPaths, "som.consent.gate.exempt-paths must be configured");
        exemptPaths = List.copyOf(exemptPaths);
    }
}
