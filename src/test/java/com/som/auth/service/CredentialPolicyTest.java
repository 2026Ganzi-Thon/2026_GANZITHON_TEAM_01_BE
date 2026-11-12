package com.som.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.auth.AuthProperties;
import com.som.auth.AuthProperties.LoginIdRule;
import com.som.auth.AuthProperties.PasswordRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CredentialPolicyTest {

    private final CredentialPolicy policy = new CredentialPolicy(new AuthProperties(
            new LoginIdRule(4, 13, "^[a-z0-9]+$"),
            new PasswordRule(10, 12, true, true)));

    @Test
    void 아이디_길이_경계() {
        assertThat(policy.checkLoginId("abc")).hasSize(1);
        assertThat(policy.checkLoginId("abcd")).isEmpty();
        assertThat(policy.checkLoginId("abcdefghij123")).isEmpty();
        assertThat(policy.checkLoginId("abcdefghij1234")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef", "솜사용자ab", "abc def", "abc_def", "abc-def!"})
    void 아이디_허용되지_않는_문자_거부(String loginId) {
        assertThat(policy.checkLoginId(loginId)).isNotEmpty().allMatch(m -> m.startsWith("loginId: "));
    }

    @Test
    void 아이디_null은_위반() {
        assertThat(policy.checkLoginId(null)).isNotEmpty();
    }

    @Test
    void 비밀번호_길이_경계() {
        assertThat(policy.checkPassword("abcdefgh1")).hasSize(1);
        assertThat(policy.checkPassword("abcdefghi1")).isEmpty();
        assertThat(policy.checkPassword("abcdefghij12")).isEmpty();
        assertThat(policy.checkPassword("abcdefghij123")).hasSize(1);
    }

    @Test
    void 비밀번호_영문만_숫자만_거부_영문숫자_통과() {
        assertThat(policy.checkPassword("abcdefghij")).hasSize(1).allMatch(m -> m.startsWith("password: "));
        assertThat(policy.checkPassword("1234567890")).hasSize(1).allMatch(m -> m.startsWith("password: "));
        assertThat(policy.checkPassword("abcde12345")).isEmpty();
    }

    @Test
    void 비밀번호_null은_위반() {
        assertThat(policy.checkPassword(null)).isNotEmpty();
    }

    @Test
    void 조합_규칙을_끄면_검사하지_않는다() {
        CredentialPolicy relaxed = new CredentialPolicy(new AuthProperties(
                new LoginIdRule(4, 13, "^[a-z0-9]+$"),
                new PasswordRule(10, 12, false, false)));
        assertThat(relaxed.checkPassword("abcdefghij")).isEmpty();
        assertThat(relaxed.checkPassword("1234567890")).isEmpty();
    }

    @Test
    void 위반_메시지에_입력값이_없다() {
        String badId = "BadID!";
        String badPw = "secretpw";
        List<String> idErrors = policy.checkLoginId(badId);
        List<String> pwErrors = policy.checkPassword(badPw);

        assertThat(idErrors).isNotEmpty().noneMatch(m -> m.contains(badId));
        assertThat(pwErrors).isNotEmpty().noneMatch(m -> m.contains(badPw));
    }
}
