package com.som.chat.repository;

import com.som.chat.domain.CardResponse;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** recommendation_responses 저장소. 모든 쿼리는 userId 조건을 포함한다. */
@Repository
public class RecommendationResponseRepository {

    public record Stored(CardResponse response, OffsetDateTime createdAt) {}

    private final NamedParameterJdbcTemplate jdbc;

    public RecommendationResponseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Stored> find(Long userId, Long messageId) {
        return jdbc.query("SELECT response, created_at FROM recommendation_responses "
                        + "WHERE user_id = :u AND message_id = :m",
                new MapSqlParameterSource("u", userId).addValue("m", messageId),
                (rs, i) -> new Stored(CardResponse.valueOf(rs.getString(1)), rs.getObject(2, OffsetDateTime.class)))
                .stream().findFirst();
    }

    public void insert(Long userId, Long messageId, String routineId, CardResponse response, OffsetDateTime now) {
        jdbc.update("INSERT INTO recommendation_responses (user_id, message_id, routine_id, response, created_at) "
                        + "VALUES (:u, :m, :r, :resp, :t)",
                new MapSqlParameterSource("u", userId).addValue("m", messageId).addValue("r", routineId)
                        .addValue("resp", response.name()).addValue("t", now));
    }

    /** 메시지 id → 응답(없으면 키 없음). */
    public Map<Long, CardResponse> findByMessages(Long userId, Collection<Long> messageIds) {
        Map<Long, CardResponse> result = new HashMap<>();
        if (messageIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT message_id, response FROM recommendation_responses "
                        + "WHERE user_id = :u AND message_id IN (:ids)",
                new MapSqlParameterSource("u", userId).addValue("ids", messageIds),
                rs -> {
                    result.put(rs.getLong(1), CardResponse.valueOf(rs.getString(2)));
                });
        return result;
    }
}
