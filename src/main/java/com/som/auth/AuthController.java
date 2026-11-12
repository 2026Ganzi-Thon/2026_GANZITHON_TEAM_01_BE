package com.som.auth;

import com.som.auth.dto.LoginRequest;
import com.som.auth.dto.LoginResponse;
import com.som.auth.dto.SignupRequest;
import com.som.auth.dto.SignupResponse;
import com.som.auth.service.AuthService;
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
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                        HttpServletResponse response) {
        Authentication auth = authService.authenticate(body.loginId(), body.password());
        establishSession(auth, request, response);
        return new LoginResponse(body.loginId());
    }

    /** 가입 직후 약관 동의 API를 부를 수 있게 바로 로그인 상태로 만든다(로그인과 같은 세션 수립 절차). */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    SignupResponse signup(@RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
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
