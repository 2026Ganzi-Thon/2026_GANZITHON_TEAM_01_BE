package com.som.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 Clock. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-07T14:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final BucketSpec SPEC = new BucketSpec(3, Duration.ofMinutes(1));

    private final MutableClock clock = new MutableClock();

    @Test
    void capacity만큼_허용하고_다음은_거부하며_재시도_초가_양수다() {
        RateLimiter limiter = new RateLimiter(100, clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("k", SPEC).allowed()).isTrue();
        }
        RateLimiter.Decision denied = limiter.tryConsume("k", SPEC);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isPositive();
    }

    @Test
    void 리필_주기가_지나면_다시_허용한다() {
        RateLimiter limiter = new RateLimiter(100, clock);
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("k", SPEC);
        }
        assertThat(limiter.tryConsume("k", SPEC).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("k", SPEC).allowed()).isTrue();
    }

    @Test
    void 키마다_버킷이_따로다() {
        RateLimiter limiter = new RateLimiter(100, clock);
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("a", SPEC);
        }

        assertThat(limiter.tryConsume("a", SPEC).allowed()).isFalse();
        assertThat(limiter.tryConsume("b", SPEC).allowed()).isTrue();
    }

    @Test
    void 키_수_상한을_넘으면_저장소_크기가_상한으로_유지된다() {
        RateLimiter limiter = new RateLimiter(5, clock);

        for (int i = 0; i < 50; i++) {
            limiter.tryConsume("key-" + i, SPEC);
        }

        assertThat(limiter.trackedKeys()).isEqualTo(5);
    }
}
