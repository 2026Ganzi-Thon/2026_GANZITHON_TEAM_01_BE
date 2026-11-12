package com.som.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 요청 횟수 제한 설정 (som.rate-limit). 대화·수집·삭제 규칙은 4단계에서 여기에 추가한다. */
@ConfigurationProperties(prefix = "som.rate-limit")
public record RateLimitProperties(int maxTrackedKeys, Login login) {
    public record Login(BucketSpec perIp, BucketSpec perLoginId) {}
}
