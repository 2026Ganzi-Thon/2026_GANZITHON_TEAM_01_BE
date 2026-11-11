package com.som.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubjectKeyGeneratorTest {

    private final SubjectKeyGenerator generator = new SubjectKeyGenerator();

    @Test
    void 호출마다_다른_값이고_64자_이하다() {
        String a = generator.next();
        String b = generator.next();

        assertThat(a).isNotBlank().isNotEqualTo(b);
        assertThat(a.length()).isLessThanOrEqualTo(64);
        assertThat(b.length()).isLessThanOrEqualTo(64);
    }
}
