package com.som.journal.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 털 깎임 연출 이벤트(사용자·날짜·출처당 1행). 모든 쿼리는 userId 조건을 포함한다. */
@Repository
public class WoolTrimEventRepository {

    private final JdbcTemplate jdbc;

    public WoolTrimEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이미 있으면 그대로 둔다(하루 한 번 연출). */
    public void recordOnce(Long userId, LocalDate localDate, String source, OffsetDateTime createdAt) {
        jdbc.update("""
                        INSERT INTO wool_trim_events (user_id, local_date, source, created_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT ON CONSTRAINT uk_wool_trim_events_user_date_source DO NOTHING
                        """,
                userId, localDate, source, createdAt);
    }

    public boolean exists(Long userId, LocalDate localDate) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM wool_trim_events WHERE user_id = ? AND local_date = ?)",
                Boolean.class, userId, localDate));
    }
}
