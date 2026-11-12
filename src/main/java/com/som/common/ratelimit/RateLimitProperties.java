package com.som.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 요청 횟수 제한 설정 (som.rate-limit). 대화·삭제 규칙은 4단계에서 여기에 추가한다. */
@ConfigurationProperties(prefix = "som.rate-limit")
public record RateLimitProperties(int maxTrackedKeys, Login login, Signup signup, Signals signals) {
    public record Login(BucketSpec perIp, BucketSpec perLoginId) {}

    /** 회원가입(IP 기준). Argon2 해시 비용 때문에 대량 가입을 막는다. */
    public record Signup(BucketSpec perIp) {}

    /** 연동 데이터 수신(세션 사용자 기준, ADR-008). */
    public record Signals(BucketSpec perUser) {}
}
