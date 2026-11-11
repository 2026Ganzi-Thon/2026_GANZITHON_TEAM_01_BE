package com.som.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.som.auth.domain.AuthProvider;
import com.som.auth.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    private User newUser(String loginId, String subjectKey) {
        return User.register(loginId, "$argon2id$dummy", subjectKey, OffsetDateTime.now(clock));
    }

    @Test
    void 저장_후_loginId로_조회() {
        users.saveAndFlush(newUser("somuser1", "subject-1"));
        em.clear();

        User found = users.findByLoginId("somuser1").orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getSubjectKey()).isEqualTo("subject-1");
        assertThat(found.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(found.getLastLoginAt()).isNull();
        assertThat(users.existsByLoginId("somuser1")).isTrue();
        assertThat(users.existsByLoginId("nobody")).isFalse();
        assertThat(users.findByLoginId("nobody")).isEmpty();
    }

    @Test
    void 중복_loginId는_거부된다() {
        users.saveAndFlush(newUser("dupuser", "subject-a"));

        assertThatThrownBy(() -> users.saveAndFlush(newUser("dupuser", "subject-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createdAt은_주입된_Clock_값이다() {
        users.saveAndFlush(newUser("clockuser", "subject-c"));
        em.clear();

        User found = users.findByLoginId("clockuser").orElseThrow();
        assertThat(found.getCreatedAt().toInstant()).isEqualTo(FIXED);
    }

    @Test
    void recordLogin은_lastLoginAt을_기록한다() {
        User user = users.saveAndFlush(newUser("loginuser", "subject-d"));
        user.recordLogin(OffsetDateTime.now(clock));
        users.flush();
        em.clear();

        assertThat(users.findByLoginId("loginuser").orElseThrow().getLastLoginAt().toInstant()).isEqualTo(FIXED);
    }

    @Test
    void toString에_passwordHash가_없다() {
        assertThat(newUser("struser", "subject-e").toString()).doesNotContain("argon2id").doesNotContain("dummy");
    }
}
