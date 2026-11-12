package com.som.auth.controller;

import com.som.auth.SessionUser;
import com.som.auth.dto.LoginRequest;
import com.som.auth.dto.LoginResponse;
import com.som.auth.dto.SignupRequest;
import com.som.auth.dto.SignupResponse;
import com.som.auth.service.AuthService;
import com.som.common.ratelimit.RateLimitProperties;
import com.som.common.ratelimit.RateLimitedException;
import com.som.common.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** JSON 회원가입·로그인·로그아웃. 세션 쿠키만 발급하고 토큰은 본문으로 주지 않는다(ADR-003). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties.Login loginLimits;
    private final RateLimitProperties.Signup signupLimits;
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(AuthService authService, RateLimiter rateLimiter, RateLimitProperties rateLimits) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.loginLimits = rateLimits.login();
        this.signupLimits = rateLimits.signup();
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                        HttpServletResponse response) {
        checkLoginRateLimit(request.getRemoteAddr(), body.loginId()); // 인증 시도 전에 검사(비밀번호 대입 차단)
        Authentication auth = authService.authenticate(body.loginId(), body.password());
        establishSession(auth, request, response);
        return new LoginResponse(body.loginId());
    }

    /**
     * IP·loginId 버킷을 둘 다 소비하고, 하나라도 부족하면 429. 성공·실패와 무관하게 시도 횟수로 센다(계정 존재 여부 비노출).
     * IP는 remoteAddr만 쓴다. X-Forwarded-For는 위조 가능하므로 아직 신뢰하지 않는다.
     */
    // TODO(DECISION): 프론트 프록시 구성 확정 후 신뢰 프록시 설정
    private void checkLoginRateLimit(String ip, String loginId) {
        RateLimiter.Decision byIp = rateLimiter.tryConsume("login:ip:" + ip, loginLimits.perIp());
        RateLimiter.Decision byLoginId = rateLimiter.tryConsume("login:id:" + loginId, loginLimits.perLoginId());
        if (!byIp.allowed() || !byLoginId.allowed()) {
            throw new RateLimitedException(Math.max(byIp.retryAfterSeconds(), byLoginId.retryAfterSeconds()));
        }
    }

    /** 가입 직후 약관 동의 API를 부를 수 있게 바로 로그인 상태로 만든다(로그인과 같은 세션 수립 절차). */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    SignupResponse signup(@RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        // 해시 계산 전에 검사(대량 가입으로 CPU 소모 차단). IP는 로그인과 같이 remoteAddr만 쓴다.
        RateLimiter.Decision byIp = rateLimiter.tryConsume("signup:ip:" + request.getRemoteAddr(), signupLimits.perIp());
        if (!byIp.allowed()) {
            throw new RateLimitedException(byIp.retryAfterSeconds());
        }
        Authentication auth = authService.signup(body.loginId(), body.password(), body.passwordConfirm());
        establishSession(auth, request, response);
        return new SignupResponse(body.loginId());
    }

    private void establishSession(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        sessionStrategy.onAuthentication(auth, request, response); // 1. 세션 고정 방지: 세션 ID 교체
        SecurityContext context = holder.createEmptyContext();       // 2. SecurityContext를 세션에 저장
        context.setAuthentication(auth);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
        authService.recordLogin(((SessionUser) auth.getPrincipal()).userId()); // 3. last_login_at (Clock 기준)
    }

    /** 세션 무효화. 세션이 없어도 204. CSRF는 필요하다. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, holder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }
}
