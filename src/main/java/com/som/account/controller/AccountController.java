package com.som.account.controller;

import com.som.account.dto.MeResponse;
import com.som.account.service.AccountService;
import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 계정 조회. 필수 동의 전에도 호출 가능해야 한다(약관 게이트 예외). 사용자는 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountService accountService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 세션은 살아 있는데 사용자 행이 없으면(삭제됨) 세션을 무효화하고 401. */
    @GetMapping
    MeResponse me(@CurrentUserId Long userId, HttpServletRequest request, HttpServletResponse response) {
        return accountService.me(userId).orElseThrow(() -> {
            logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            return new ApiException(ErrorCode.UNAUTHORIZED);
        });
    }
}
