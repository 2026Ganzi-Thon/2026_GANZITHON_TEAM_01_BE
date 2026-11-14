package com.som.account.controller;

import com.som.account.dto.DeletionConfirmRequest;
import com.som.account.dto.DeletionStatusResponse;
import com.som.account.dto.MeResponse;
import com.som.account.dto.NicknameRequest;
import com.som.account.service.AccountService;
import com.som.account.service.DeletionScope;
import com.som.account.service.DeletionService;
import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 계정 조회·기록 삭제·처리 상태. 필수 동의 전에도 호출 가능해야 한다(약관 게이트 예외).
 * 사용자는 @CurrentUserId로만 정한다.
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountService accountService;
    private final DeletionService deletionService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties.Deletion deletionLimits;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AccountController(AccountService accountService, DeletionService deletionService, RateLimiter rateLimiter,
                             RateLimitProperties rateLimits) {
        this.accountService = accountService;
        this.deletionService = deletionService;
        this.rateLimiter = rateLimiter;
        this.deletionLimits = rateLimits.deletion();
    }

    /** 세션은 살아 있는데 사용자 행이 없으면(삭제됨) 세션을 무효화하고 401. */
    @GetMapping
    MeResponse me(@CurrentUserId Long userId, HttpServletRequest request, HttpServletResponse response) {
        return accountService.me(userId).orElseThrow(() -> {
            logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            return new ApiException(ErrorCode.UNAUTHORIZED);
        });
    }

    @PatchMapping
    MeResponse updateNickname(@CurrentUserId Long userId,
                              @RequestBody NicknameRequest body) {
        return accountService.updateNickname(userId, body.nickname());
    }

    @DeleteMapping("/data")
    DeletionStatusResponse deleteData(@CurrentUserId Long userId, @RequestBody DeletionConfirmRequest body) {
        checkDeletion(userId, body);
        return deletionService.delete(userId, DeletionScope.DATA);
    }

    /** 탈퇴: 모든 데이터 즉시 삭제 → 세션 무효화. 탈퇴 후에는 상태를 조회할 수 없으므로 응답에 담는다. */
    @DeleteMapping
    DeletionStatusResponse withdraw(@CurrentUserId Long userId, @RequestBody DeletionConfirmRequest body,
                                    HttpServletRequest request, HttpServletResponse response) {
        checkDeletion(userId, body);
        DeletionStatusResponse result = deletionService.delete(userId, DeletionScope.ACCOUNT);
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return result;
    }

    /** 가장 최근 삭제 처리 상태. 없으면 404. */
    @GetMapping("/deletion-status")
    DeletionStatusResponse deletionStatus(@CurrentUserId Long userId) {
        return deletionService.latest(userId);
    }

    private void checkDeletion(Long userId, DeletionConfirmRequest body) {
        RateLimiter.Decision decision = rateLimiter.tryConsume("deletion:user:" + userId, deletionLimits.perUser());
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
        if (body == null || !Boolean.TRUE.equals(body.confirm())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "confirm: 삭제하려면 true로 확인해 주세요");
        }
    }
}
