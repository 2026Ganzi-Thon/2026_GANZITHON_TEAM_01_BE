package com.som.account.repository;

import com.som.account.dto.DeletionStatusResponse;
import java.time.OffsetDateTime;
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

    public void insert(String subjectHash, String scope, String status, OffsetDateTime requestedAt,
                       OffsetDateTime completedAt) {
        jdbc.update("INSERT INTO deletion_requests (subject_hash, scope, status, requested_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?)", subjectHash, scope, status, requestedAt, completedAt);
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
