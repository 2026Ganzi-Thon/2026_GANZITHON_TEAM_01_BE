package com.som.recovery.repository;

import com.som.recovery.dto.RecoveryShareResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 회복 팁 익명 공유. 목록은 모든 사용자의 글이고, 쓰기·지우기는 userId 조건으로 본인 글만 다룬다. */
@Repository
public class RecoveryShareRepository {

    private final JdbcTemplate jdbc;

    public RecoveryShareRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RecoveryShareResponse> recent(Long viewerId, int limit) {
        return jdbc.query("""
                SELECT id, message, user_id = ? AS mine FROM recovery_shares
                ORDER BY id DESC LIMIT ?
                """, (row, index) -> new RecoveryShareResponse(row.getLong("id"), row.getString("message"),
                row.getBoolean("mine")), viewerId, limit);
    }

    public long insert(Long userId, String message, OffsetDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO recovery_shares (user_id, message, created_at) VALUES (?, ?, ?) RETURNING id
                """, Long.class, userId, message, createdAt);
    }

    /** 본인 글이면 지우고 true, 없거나 남의 글이면 false. */
    public boolean deleteOwn(Long userId, long id) {
        return jdbc.update("DELETE FROM recovery_shares WHERE id = ? AND user_id = ?", id, userId) > 0;
    }
}
