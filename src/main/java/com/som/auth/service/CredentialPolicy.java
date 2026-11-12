package com.som.auth.service;

import com.som.auth.AuthProperties;
import com.som.auth.AuthProperties.LoginIdRule;
import com.som.auth.AuthProperties.PasswordRule;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** 아이디·비밀번호 규칙 검사. 규칙 값은 som.auth 설정에서만 온다. 위반 메시지에 입력값을 넣지 않는다. */
@Component
@EnableConfigurationProperties(AuthProperties.class)
public class CredentialPolicy {

    private final LoginIdRule loginIdRule;
    private final PasswordRule passwordRule;
    private final Pattern loginIdPattern;

    public CredentialPolicy(AuthProperties props) {
        this.loginIdRule = props.loginId();
        this.passwordRule = props.password();
        this.loginIdPattern = Pattern.compile(loginIdRule.pattern());
    }

    /** 위반 항목을 "필드: 이유" 목록으로 반환. 비어 있으면 통과. */
    public List<String> checkLoginId(String loginId) {
        if (loginId == null) {
            return List.of("loginId: 필수입니다");
        }
        List<String> errors = new ArrayList<>();
        int len = loginId.codePointCount(0, loginId.length());
        if (len < loginIdRule.minLength() || len > loginIdRule.maxLength()) {
            errors.add("loginId: " + loginIdRule.minLength() + "~" + loginIdRule.maxLength() + "자여야 합니다");
        }
        if (!loginIdPattern.matcher(loginId).matches()) {
            errors.add("loginId: 허용되지 않는 문자가 있습니다");
        }
        return errors;
    }

    public List<String> checkPassword(String password) {
        if (password == null) {
            return List.of("password: 필수입니다");
        }
        List<String> errors = new ArrayList<>();
        int len = password.codePointCount(0, password.length());
        if (len < passwordRule.minLength() || len > passwordRule.maxLength()) {
            errors.add("password: " + passwordRule.minLength() + "~" + passwordRule.maxLength() + "자여야 합니다");
        }
        boolean missingLetter = passwordRule.requireLetter() && password.chars().noneMatch(CredentialPolicy::isAsciiLetter);
        boolean missingDigit = passwordRule.requireDigit() && password.chars().noneMatch(CredentialPolicy::isAsciiDigit);
        if (missingLetter || missingDigit) {
            errors.add("password: 영문과 숫자를 각각 1자 이상 포함해야 합니다");
        }
        return errors;
    }

    private static boolean isAsciiLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }
}
