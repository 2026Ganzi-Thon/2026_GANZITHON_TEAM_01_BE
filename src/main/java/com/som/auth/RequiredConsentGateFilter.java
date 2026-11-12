package com.som.auth;

import com.som.common.error.ErrorCode;
import com.som.consent.service.RequiredConsentChecker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 필수 약관 게이트: 인증된 사용자의 /api/** 요청 중 som.consent.gate.exempt-paths가 아닌 것은
 * 필수 약관 3개를 모두 동의한 경우에만 통과시키고, 아니면 403 CONSENT_REQUIRED.
 * 동의 상태는 요청마다 조회한다(세션 캐시 금지 — 철회가 즉시 반영돼야 한다). 미인증 요청은 건드리지 않는다.
 * 선택 연동 동의는 보지 않는다(A-02). SecurityConfig가 AuthorizationFilter 다음에 등록한다(서블릿 필터 빈 아님).
 */
public class RequiredConsentGateFilter extends OncePerRequestFilter {

    private static final String API_PATTERN = "/api/**";

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RequiredConsentChecker checker;
    private final ConsentGateProperties properties;
    private final SecurityErrorHandler errorHandler;

    public RequiredConsentGateFilter(RequiredConsentChecker checker, ConsentGateProperties properties,
                                     SecurityErrorHandler errorHandler) {
        this.checker = checker;
        this.properties = properties;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = currentUserId();
        if (userId != null && isGated(path(request)) && !checker.isCompleted(userId)) {
            errorHandler.write(request, response, ErrorCode.CONSENT_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isGated(String path) {
        return matcher.match(API_PATTERN, path)
                && properties.exemptPaths().stream().noneMatch(p -> matcher.match(p, path));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof SessionUser user) {
            return user.userId();
        }
        return null;
    }
}
