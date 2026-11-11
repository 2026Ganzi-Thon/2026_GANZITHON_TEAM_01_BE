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
