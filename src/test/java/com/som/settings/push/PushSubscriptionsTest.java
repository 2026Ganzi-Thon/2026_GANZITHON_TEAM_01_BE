package com.som.settings.push;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushSubscriptionsTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PushSubscriptions push;

    @Test
    void claimsAnEnabledReminderOncePerDayAndStopsAfterUnsubscribe() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (login_id, password_hash, subject_key, auth_provider, created_at)
                VALUES ('pushtest', 'not-a-real-password', 'push-subscription-test', 'LOCAL', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class);
        String endpoint = "https://web.push.apple.com/test-subscription";
        push.save(userId, endpoint, "key", "auth");
        jdbc.update("""
                INSERT INTO notification_settings (user_id, enabled, time, updated_at)
                VALUES (?, FALSE, TIME '20:00', CURRENT_TIMESTAMP)
                """, userId);

        LocalDate date = LocalDate.of(2026, 11, 18);
        LocalTime time = LocalTime.of(20, 0);
        assertThat(push.claimDue(date, time)).isEmpty();
        jdbc.update("UPDATE notification_settings SET enabled = TRUE WHERE user_id = ?", userId);
        assertThat(push.claimDue(date, time)).extracting(PushSubscriptions.Target::endpoint).containsExactly(endpoint);
        assertThat(push.claimDue(date, time.plusMinutes(1))).isEmpty();
        assertThat(push.claimDue(date.plusDays(1), time)).hasSize(1);
        push.remove(userId, endpoint);
        assertThat(push.claimDue(date.plusDays(2), time)).isEmpty();
    }
}
