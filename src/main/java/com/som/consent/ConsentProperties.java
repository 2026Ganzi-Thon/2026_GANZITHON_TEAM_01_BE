package com.som.consent;

import com.som.consent.domain.RequiredConsent;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 동의 설정 (som.consent). 정책 버전은 클라이언트가 아니라 여기 값만 기록한다. */
@ConfigurationProperties(prefix = "som.consent")
public record ConsentProperties(Map<RequiredConsent, Policy> required) {
    public record Policy(String policyVersion) {}
}
