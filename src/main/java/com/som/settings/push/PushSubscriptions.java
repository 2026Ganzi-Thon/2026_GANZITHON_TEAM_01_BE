package com.som.settings.push;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PushSubscriptions {

    private final JdbcTemplate jdbc;

    PushSubscriptions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(Long userId, String endpoint, String key, String auth) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "web_push:" + userId);
        Boolean allowed = jdbc.queryForObject("""
                SELECT count(*) < 10 OR COALESCE(bool_or(endpoint = ?), FALSE)
                FROM push_subscriptions WHERE user_id = ?
                """, Boolean.class, endpoint, userId);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new ApiException(ErrorCode.CONFLICT, "한 계정에 최대 10개의 알림 기기를 등록할 수 있어요.");
        }
        int written = jdbc.update("""
                INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth
                WHERE push_subscriptions.user_id = EXCLUDED.user_id
                """, userId, endpoint, key, auth);
        if (written == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 다른 계정에서 등록한 알림 구독입니다.");
        }
    }

    void remove(Long userId, String endpoint) {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND endpoint = ?", userId, endpoint);
    }

    List<Target> claimDue(LocalDate today, LocalTime now) {
        LocalTime earliest = now.minusMinutes(Math.min(now.toSecondOfDay() / 60, 5));
        return jdbc.query("""
                WITH due AS (
                    SELECT p.id FROM push_subscriptions p
                    JOIN notification_settings n ON n.user_id = p.user_id
                    WHERE n.enabled AND (p.last_attempt_date IS NULL OR p.last_attempt_date < ?)
                      AND COALESCE(n.time, CASE n.slot
                          WHEN 'MORNING' THEN TIME '09:00'
                          WHEN 'LUNCH' THEN TIME '12:30'
                          WHEN 'AFTER_WORK' THEN TIME '18:30'
                          WHEN 'BEFORE_SLEEP' THEN TIME '22:00'
                      END) BETWEEN ? AND ?
                    ORDER BY p.id
                    LIMIT 20 FOR UPDATE OF p SKIP LOCKED
                )
                UPDATE push_subscriptions p SET last_attempt_date = ?
                FROM due WHERE p.id = due.id
                RETURNING p.id, p.endpoint, p.p256dh, p.auth
                """, (row, index) -> new Target(row.getLong(1), row.getString(2), row.getString(3), row.getString(4)),
                today, earliest, now, today);
    }

    void removeExpired(Target target) {
        jdbc.update("DELETE FROM push_subscriptions WHERE id = ? AND p256dh = ? AND auth = ?",
                target.id(), target.key(), target.auth());
    }

    record Target(long id, String endpoint, String key, String auth) {
        @Override
        public String toString() {
            return "PushTarget[id=" + id + "]";
        }
    }
}
