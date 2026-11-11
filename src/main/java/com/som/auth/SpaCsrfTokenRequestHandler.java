package com.som.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Spring Security 6 공식 문서 "Single-Page Applications" 패턴의 토큰 요청 처리기.
 * - 요청 속성(= /auth/csrf 응답 본문)의 토큰은 BREACH 방지를 위해 XOR 마스킹한다.
 * - 매 요청 토큰을 로드해 XSRF-TOKEN 쿠키가 항상 설정되게 한다.
 * - 헤더 값은 쿠키 원값(SPA 기본 동작)이면 그대로, 본문의 마스킹 값이면 XOR 해제해 비교한다.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (!StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return xor.resolveCsrfTokenValue(request, csrfToken); // 폼 파라미터 등
        }
        // 마스킹 값이 아니면(디코딩 길이가 맞지 않으면) XOR 해제가 null이므로 원값으로 비교한다.
        String unmasked = xor.resolveCsrfTokenValue(request, csrfToken);
        return unmasked != null ? unmasked : plain.resolveCsrfTokenValue(request, csrfToken);
    }
}
