package com.som.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 요청 횟수 제한 설정 (som.rate-limit). */
@ConfigurationProperties(prefix = "som.rate-limit")
public record RateLimitProperties(int maxTrackedKeys, Login login, Signup signup, Signals signals, Chat chat,
                                  Deletion deletion, RecoveryShare recoveryShare) {
    public record Login(BucketSpec perIp, BucketSpec perLoginId) {}

    /** 회원가입(IP 기준). Argon2 해시 비용 때문에 대량 가입을 막는다. */
    public record Signup(BucketSpec perIp) {}

    /** 연동 데이터 수신(세션 사용자 기준, ADR-008). */
    public record Signals(BucketSpec perUser) {}

    /** 대화 전송(세션 사용자 기준, A_SPEC §11 분당/일일). */
    public record Chat(BucketSpec perUserMinute, BucketSpec perUserDay) {}

    /** 기록 삭제·탈퇴(세션 사용자 기준, 두 API가 같은 버킷, A_SPEC §11). */
    public record Deletion(BucketSpec perUser) {}

    /** 회복 팁 익명 공유 글쓰기(세션 사용자 기준). 조회는 제한하지 않는다. */
    public record RecoveryShare(BucketSpec perUser) {}
}
