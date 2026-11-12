package com.som.routine.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** routine_recommendations 저장소. 모든 쿼리는 userId 조건을 포함한다. */
@Repository
public class RoutineRecommendationRepository {

    public record Stored(LocalDate localDate, String routineIdsJson) {}

    private final JdbcTemplate jdbc;

    public RoutineRecommendationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Stored> find(Long userId, LocalDate localDate) {
        return jdbc.query("SELECT local_date, routine_ids FROM routine_recommendations WHERE user_id = ? AND local_date = ?",
                (rs, i) -> new Stored(rs.getObject(1, LocalDate.class), rs.getString(2)), userId, localDate)
                .stream().findFirst();
    }

    /** before 이전 날짜 중 가장 최근 추천. */
    public Optional<Stored> findLatestBefore(Long userId, LocalDate before) {
        return jdbc.query("""
                        SELECT local_date, routine_ids FROM routine_recommendations
                        WHERE user_id = ? AND local_date < ? ORDER BY local_date DESC LIMIT 1
                        """,
                (rs, i) -> new Stored(rs.getObject(1, LocalDate.class), rs.getString(2)), userId, before)
                .stream().findFirst();
    }

    /** 이미 있으면 아무것도 하지 않는다(동시 첫 조회). */
    public void insertIfAbsent(Long userId, LocalDate localDate, String routineIdsJson, String reason,
                               OffsetDateTime createdAt) {
        jdbc.update("""
                        INSERT INTO routine_recommendations (user_id, local_date, routine_ids, reason, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT ON CONSTRAINT uk_routine_recommendations_user_date DO NOTHING
                        """,
                userId, localDate, routineIdsJson, reason, createdAt);
    }
}
