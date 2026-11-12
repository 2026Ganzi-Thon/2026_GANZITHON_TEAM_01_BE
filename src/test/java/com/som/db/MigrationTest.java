package com.som.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MigrationTest {

    private static final String INSERT_USER = """
            INSERT INTO users (login_id, password_hash, subject_key, auth_provider, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v1MigrationIsAppliedSuccessfully() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    void usersTableHasExpectedColumns() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'users'
                """);
        Map<String, Map<String, Object>> columns = rows.stream()
                .collect(Collectors.toMap(r -> (String) r.get("column_name"), r -> r));

        assertThat(columns).containsOnlyKeys(
                "id", "login_id", "password_hash", "subject_key",
                "auth_provider", "created_at", "last_login_at");

        assertColumn(columns, "id", "bigint", "NO", null);
        assertColumn(columns, "login_id", "character varying", "NO", 64);
        assertColumn(columns, "password_hash", "character varying", "NO", 255);
        assertColumn(columns, "subject_key", "character varying", "NO", 64);
        assertColumn(columns, "auth_provider", "character varying", "NO", 20);
        assertColumn(columns, "created_at", "timestamp with time zone", "NO", null);
        assertColumn(columns, "last_login_at", "timestamp with time zone", "YES", null);
    }

    @Test
    void createdAtHasNoDatabaseDefault() {
        String defaultValue = jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'created_at'
                """, String.class);
        assertThat(defaultValue).isNull();
    }

    @Test
    void phase2MigrationsCreateIdempotencyAndCheckinTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN ('idempotency_keys', 'checkins', 'checkin_stressors')
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder("idempotency_keys", "checkins", "checkin_stressors");

        List<String> checkinColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'checkins'
                """, String.class);
        // 자유 입력(메모·텍스트) 컬럼이 없다(A_SPEC §6)
        assertThat(checkinColumns).containsExactlyInAnyOrder(
                "id", "user_id", "local_date", "mood", "support_need", "status", "created_at", "updated_at");
    }

    @Test
    void duplicateLoginIdIsRejected() {
        insertUser("dupuser", "subject-a");

        assertThatThrownBy(() -> insertUser("dupuser", "subject-b"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSubjectKeyIsRejected() {
        insertUser("user-a", "same-subject");

        assertThatThrownBy(() -> insertUser("user-b", "same-subject"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userConsentsTableHasExpectedColumnsAndIndex() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'user_consents'
                """);
        Map<String, Map<String, Object>> columns = rows.stream()
                .collect(Collectors.toMap(r -> (String) r.get("column_name"), r -> r));

        assertThat(columns).containsOnlyKeys(
                "id", "user_id", "consent_type", "source", "items", "purpose",
                "retention", "is_granted", "policy_version", "changed_at");
        assertColumn(columns, "id", "bigint", "NO", null);
        assertColumn(columns, "user_id", "bigint", "NO", null);
        assertColumn(columns, "consent_type", "character varying", "NO", 40);
        assertColumn(columns, "source", "character varying", "YES", 40);
        assertColumn(columns, "items", "character varying", "YES", 1000);
        assertColumn(columns, "purpose", "character varying", "YES", 500);
        assertColumn(columns, "retention", "character varying", "YES", 200);
        assertColumn(columns, "is_granted", "boolean", "NO", null);
        assertColumn(columns, "policy_version", "character varying", "NO", 40);
        assertColumn(columns, "changed_at", "timestamp with time zone", "NO", null);

        String indexDef = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'user_consents'
                  AND indexname = 'ix_user_consents_user_type_source_changed'
                """, String.class);
        assertThat(indexDef).contains("(user_id, consent_type, source, changed_at)");
    }

    @Test
    void userConsentRequiresExistingUser() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO user_consents (user_id, consent_type, is_granted, policy_version, changed_at)
                VALUES (?, 'AGE_14_PLUS', true, 'v1', ?)
                """, -1L, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void connectionsTableHasExpectedColumns() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'connections'
                """);
        Map<String, Map<String, Object>> columns = rows.stream()
                .collect(Collectors.toMap(r -> (String) r.get("column_name"), r -> r));

        assertThat(columns).containsOnlyKeys(
                "id", "user_id", "source", "provider", "status", "connected_at", "revoked_at");
        assertColumn(columns, "id", "bigint", "NO", null);
        assertColumn(columns, "user_id", "bigint", "NO", null);
        assertColumn(columns, "source", "character varying", "NO", 40);
        assertColumn(columns, "provider", "character varying", "NO", 20);
        assertColumn(columns, "status", "character varying", "NO", 20);
        assertColumn(columns, "connected_at", "timestamp with time zone", "YES", null);
        assertColumn(columns, "revoked_at", "timestamp with time zone", "YES", null);
    }

    @Test
    void connectionIsUniquePerUserAndSource() {
        insertUser("conn-user", "conn-subject");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'conn-user'", Long.class);
        jdbc.update(INSERT_CONNECTION, userId, "CONNECTED");

        assertThatThrownBy(() -> jdbc.update(INSERT_CONNECTION, userId, "REVOKED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void connectionRequiresExistingUser() {
        assertThatThrownBy(() -> jdbc.update(INSERT_CONNECTION, -1L, "CONNECTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void connectionStatusIsLimitedToConnectedOrRevoked() {
        insertUser("conn-user2", "conn-subject2");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'conn-user2'", Long.class);

        assertThatThrownBy(() -> jdbc.update(INSERT_CONNECTION, userId, "NOT_CONNECTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailySignalsTableHasExpectedColumnsAndIndex() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'daily_signals'
                """);
        Map<String, Map<String, Object>> columns = rows.stream()
                .collect(Collectors.toMap(r -> (String) r.get("column_name"), r -> r));

        // '안 보는 것'(제목·참석자·위치·앱 이름 등)이나 세부 로그를 담을 컬럼이 없다
        assertThat(columns).containsOnlyKeys("id", "user_id", "local_date", "source", "metric_key", "value",
                "value_at", "unit", "hour_bucket", "provider", "collected_at");
        assertColumn(columns, "id", "bigint", "NO", null);
        assertColumn(columns, "user_id", "bigint", "NO", null);
        assertColumn(columns, "local_date", "date", "NO", null);
        assertColumn(columns, "source", "character varying", "NO", 40);
        assertColumn(columns, "metric_key", "character varying", "NO", 40);
        assertColumn(columns, "value", "numeric", "YES", null);
        assertColumn(columns, "value_at", "timestamp with time zone", "YES", null);
        assertColumn(columns, "unit", "character varying", "YES", 20);
        assertColumn(columns, "hour_bucket", "smallint", "YES", null);
        assertColumn(columns, "provider", "character varying", "NO", 20);
        assertColumn(columns, "collected_at", "timestamp with time zone", "NO", null);

        Integer index = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'daily_signals'
                  AND indexdef LIKE '%(user_id, local_date)'
                """, Integer.class);
        assertThat(index).isEqualTo(1);
    }

    @Test
    void dailySignalKeyTreatsNullHourBucketAsEqual() {
        insertUser("sig-user", "sig-subject");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'sig-user'", Long.class);
        jdbc.update(INSERT_SIGNAL, userId, "STEPS_DAILY", null);

        assertThatThrownBy(() -> jdbc.update(INSERT_SIGNAL, userId, "STEPS_DAILY", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailySignalAllowsDifferentHourBuckets() {
        insertUser("sig-user2", "sig-subject2");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'sig-user2'", Long.class);
        jdbc.update(INSERT_SIGNAL, userId, "SCREEN_TIME_MINUTES", 0);
        jdbc.update(INSERT_SIGNAL, userId, "SCREEN_TIME_MINUTES", 1);

        assertThatThrownBy(() -> jdbc.update(INSERT_SIGNAL, userId, "SCREEN_TIME_MINUTES", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailySignalRequiresExistingUser() {
        assertThatThrownBy(() -> jdbc.update(INSERT_SIGNAL, -1L, "STEPS_DAILY", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static final String INSERT_SIGNAL = """
            INSERT INTO daily_signals (user_id, local_date, source, metric_key, value, unit, hour_bucket,
                                       provider, collected_at)
            VALUES (?, DATE '2026-11-11', 'X', ?, 1, 'count', ?::smallint, 'DEMO', now())
            """;

    private static final String INSERT_CONNECTION =
            "INSERT INTO connections (user_id, source, provider, status) VALUES (?, 'SLEEP', 'DEMO', ?)";

    private void insertUser(String loginId, String subjectKey) {
        jdbc.update(INSERT_USER, loginId, "hash", subjectKey, "LOCAL", OffsetDateTime.now());
    }

    private static void assertColumn(Map<String, Map<String, Object>> columns, String name,
                                     String dataType, String nullable, Integer maxLength) {
        Map<String, Object> col = columns.get(name);
        assertThat(col.get("data_type")).as(name + " type").isEqualTo(dataType);
        assertThat(col.get("is_nullable")).as(name + " nullable").isEqualTo(nullable);
        Object length = col.get("character_maximum_length");
        if (maxLength == null) {
            assertThat(length).as(name + " length").isNull();
        } else {
            assertThat(((Number) length).intValue()).as(name + " length").isEqualTo(maxLength);
        }
    }
}
