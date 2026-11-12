package com.som.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 계정 규칙 설정 (som.auth). 길이·패턴·조합 규칙은 yml에서만 정한다(A_SPEC §3). */
@ConfigurationProperties(prefix = "som.auth")
public record AuthProperties(LoginIdRule loginId, PasswordRule password) {
    public record LoginIdRule(int minLength, int maxLength, String pattern) {}
    public record PasswordRule(int minLength, int maxLength, boolean requireLetter, boolean requireDigit) {}
}
