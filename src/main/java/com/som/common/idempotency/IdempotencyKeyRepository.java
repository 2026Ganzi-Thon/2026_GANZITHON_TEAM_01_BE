package com.som.common.idempotency;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** idempotency_keys 저장소. 모든 쿼리는 userId 조건을 포함한다. */
@Repository
public class IdempotencyKeyRepository {

    public record StoredResponse(String requestHash, int status, String body, OffsetDateTime createdAt) {}

    private final JdbcTemplate jdbc;

    public IdempotencyKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 (userId, endpoint, key) 요청끼리 트랜잭션 끝까지 직렬화한다. */
    public void lock(Long userId, String endpoint, String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                "idempotency:" + userId + ":" + endpoint + ":" + key);
    }

    public Optional<StoredResponse> find(Long userId, String endpoint, String key) {
        return jdbc.query("""
                        SELECT request_hash, response_status, response_body, created_at FROM idempotency_keys
                        WHERE user_id = ? AND endpoint = ? AND idem_key = ?
                        """,
                (rs, i) -> new StoredResponse(rs.getString(1), rs.getInt(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)),
                userId, endpoint, key).stream().findFirst();
    }

    public void delete(Long userId, String endpoint, String key) {
        jdbc.update("DELETE FROM idempotency_keys WHERE user_id = ? AND endpoint = ? AND idem_key = ?",
                userId, endpoint, key);
    }

    public void insert(Long userId, String endpoint, String key, String requestHash, int status, String body,
                       OffsetDateTime createdAt) {
        jdbc.update("""
                        INSERT INTO idempotency_keys
                            (user_id, endpoint, idem_key, request_hash, response_status, response_body, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, endpoint, key, requestHash, status, body, createdAt);
    }
}
