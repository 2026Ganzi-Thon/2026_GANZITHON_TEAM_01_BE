package com.som.connection.repository;

import com.som.connection.domain.ConnectionProvider;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

/**
 * daily_signals 저장소. 키(user_id, local_date, source, metric_key, hour_bucket) 단위 upsert는
 * NULLS NOT DISTINCT 유니크 제약을 arbiter로 쓰는 ON CONFLICT로 처리한다. 모든 쿼리는 userId 조건을 포함한다.
 * 값은 로그에 남기지 않는다.
 */
@Repository
public class DailySignalRepository {

    private static final String UPSERT = """
            INSERT INTO daily_signals (user_id, local_date, source, metric_key, value, value_at, unit, hour_bucket,
                                       provider, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uk_daily_signals_key DO UPDATE
            SET value = EXCLUDED.value, value_at = EXCLUDED.value_at, unit = EXCLUDED.unit,
                provider = EXCLUDED.provider, collected_at = EXCLUDED.collected_at
            """;

    private final JdbcTemplate jdbc;

    public DailySignalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(Long userId, LocalDate localDate, DataSource source, Metric metric, BigDecimal value,
                       OffsetDateTime valueAt, Integer hourBucket, ConnectionProvider provider,
                       OffsetDateTime collectedAt) {
        jdbc.update(UPSERT, userId, localDate, source.name(), metric.name(),
                new SqlParameterValue(Types.NUMERIC, value),
                new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, valueAt),
                new SqlParameterValue(Types.VARCHAR, metric.unit()),
                new SqlParameterValue(Types.SMALLINT, hourBucket),
                provider.name(),
                new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, collectedAt));
    }

    /** 시간대가 없는 시각 지표(SLEEP_ONSET·SLEEP_WAKE)의 저장값. */
    public Optional<OffsetDateTime> findValueAt(Long userId, LocalDate localDate, DataSource source, Metric metric) {
        return jdbc.query("""
                        SELECT value_at FROM daily_signals
                        WHERE user_id = ? AND local_date = ? AND source = ? AND metric_key = ? AND hour_bucket IS NULL
                        """,
                        (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
                        userId, localDate, source.name(), metric.name())
                .stream().filter(Objects::nonNull).findFirst();
    }

    public int deleteByUserIdAndSource(Long userId, DataSource source) {
        return jdbc.update("DELETE FROM daily_signals WHERE user_id = ? AND source = ?", userId, source.name());
    }
}
