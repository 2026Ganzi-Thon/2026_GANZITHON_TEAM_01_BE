package com.som.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 메모리 기반 키별 토큰 버킷(ADR-008). 키는 호출자가 용도별 접두사를 붙여 만든다(예: "login:ip:...").
 * 키 저장소는 maxTrackedKeys를 넘지 않는다: 가장 오래 쓰이지 않은 키부터 버린다(무작위 키로 메모리 고갈 방지).
 * 키(IP·loginId 등 식별 정보)를 로그에 남기지 마라.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private final TimeMeter timeMeter;
    private final Map<String, Bucket> buckets;

    @Autowired
    public RateLimiter(RateLimitProperties props, Clock clock) {
        this(props.maxTrackedKeys(), clock);
    }

    RateLimiter(int maxTrackedKeys, Clock clock) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxTrackedKeys;
            }
        };
    }

    /** key별 버킷에서 1개 소비. 실패 시 다시 시도까지 남은 초(1 이상)를 담아 반환 */
    public Decision tryConsume(String key, BucketSpec spec) {
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(key, k -> newBucket(spec));
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Decision(true, 0);
        }
        long seconds = (probe.getNanosToWaitForRefill() + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
        return new Decision(false, Math.max(1, seconds));
    }

    int trackedKeys() {
        synchronized (buckets) {
            return buckets.size();
        }
    }

    private Bucket newBucket(BucketSpec spec) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(spec.capacity())
                        .refillGreedy(spec.capacity(), spec.refillPeriod()).build())
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    /** Bucket4j 시간 소스를 주입된 Clock에 맞춘다(ADR-007, 테스트에서 시간 이동 가능). */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
