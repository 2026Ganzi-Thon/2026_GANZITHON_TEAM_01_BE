package com.som.recovery.controller;

import com.som.auth.web.CurrentUserId;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import com.som.recovery.dto.RecoveryShareRequest;
import com.som.recovery.dto.RecoveryShareResponse;
import com.som.recovery.service.RecoveryShareService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회복 팁 익명 공유: 최근 글 목록, 한 줄 공유(요청 제한), 본인 글 삭제. */
@RestController
@RequestMapping("/api/v1/recovery-shares")
public class RecoveryShareController {

    private final RecoveryShareService service;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties.RecoveryShare limits;

    public RecoveryShareController(RecoveryShareService service, RateLimiter rateLimiter,
                                   RateLimitProperties rateLimits) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.limits = rateLimits.recoveryShare();
    }

    @GetMapping
    List<RecoveryShareResponse> list(@CurrentUserId Long userId) {
        return service.recent(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecoveryShareResponse share(@CurrentUserId Long userId, @Valid @RequestBody RecoveryShareRequest request) {
        RateLimiter.Decision decision = rateLimiter.tryConsume("recovery-share:user:" + userId, limits.perUser());
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
        return service.share(userId, request.message());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUserId Long userId, @PathVariable long id) {
        service.remove(userId, id);
    }
}
