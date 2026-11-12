package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void argon2id로_해시하고_matches가_동작한다() {
        String hash = encoder.encode("abcde12345");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(encoder.matches("abcde12345", hash)).isTrue();
        assertThat(encoder.matches("abcde12346", hash)).isFalse();
    }
}
