package com.som.account.repository;

import com.som.account.dto.DeletionStatusResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** deletion_requests(익명화된 처리 기록). 사용자는 subjectKey 해시로만 찾는다. */
@Repository
public class DeletionRequestRepository {

    private final JdbcTemplate jdbc;

    public DeletionRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Pending(long id, String subjectKey) {
        @Override
        public String toString() {
            return "Pending[id=" + id + "]";
        }
    }

    public long enqueue(String subjectHash, String subjectKey, String scope, OffsetDateTime requestedAt,
                        OffsetDateTime retryAt) {
        return jdbc.queryForObject("""
                INSERT INTO deletion_requests (subject_hash, scope, status, requested_at,
                                               pending_subject_key, next_attempt_at)
                VALUES (?, ?, 'ENGINE_PENDING', ?, ?, ?) RETURNING id
                """, Long.class, subjectHash, scope, requestedAt, subjectKey, retryAt);
    }

    public List<Pending> claimDue(OffsetDateTime now, OffsetDateTime retryAt) {
        return jdbc.query("""
                WITH due AS (
                    SELECT id FROM deletion_requests
                    WHERE status = 'ENGINE_PENDING' AND pending_subject_key IS NOT NULL AND next_attempt_at <= ?
                    ORDER BY next_attempt_at, id LIMIT 10 FOR UPDATE SKIP LOCKED
                )
                UPDATE deletion_requests AS request SET next_attempt_at = ?
                FROM due WHERE request.id = due.id
                RETURNING request.id, request.pending_subject_key
                """, (rs, i) -> new Pending(rs.getLong(1), rs.getString(2)), now, retryAt);
    }

    public void complete(long id, OffsetDateTime at) {
        jdbc.update("""
                UPDATE deletion_requests
                SET status = 'COMPLETED', completed_at = ?, pending_subject_key = NULL, next_attempt_at = NULL
                WHERE id = ? AND status = 'ENGINE_PENDING'
                """, at, id);
    }

    public Optional<DeletionStatusResponse> findLatest(String subjectHash) {
        return jdbc.query("""
                        SELECT scope, status, requested_at, completed_at FROM deletion_requests
                        WHERE subject_hash = ? ORDER BY requested_at DESC, id DESC LIMIT 1
                        """,
                (rs, i) -> new DeletionStatusResponse(rs.getString(1), rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class)),
                subjectHash).stream().findFirst();
    }
}
