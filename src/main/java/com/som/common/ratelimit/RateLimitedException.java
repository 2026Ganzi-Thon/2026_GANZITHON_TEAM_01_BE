package com.som.common.ratelimit;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;

/** 429 RATE_LIMITED. 공통 에러 처리기가 retryAfterSeconds를 Retry-After 헤더로 내보낸다. */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
