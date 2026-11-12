package com.som.common.ratelimit;

import java.time.Duration;

/** 버킷 규칙: refillPeriod 동안 capacity개가 고르게 다시 찬다. 값은 yml(som.rate-limit)에서만 정한다. */
public record BucketSpec(long capacity, Duration refillPeriod) {}
