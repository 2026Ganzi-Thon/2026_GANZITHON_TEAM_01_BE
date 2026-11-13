package com.som.connection.repository;

import com.som.connection.domain.ConnectionProvider;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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

    /** 하루치 저장 행 하나(값은 로그에 남기지 않는다). */
    public record Row(DataSource source, Metric metric, BigDecimal value, OffsetDateTime valueAt, String unit,
                      Integer hourBucket) {
        @Override
        public String toString() {
            return "Row[source=" + source + ", metric=" + metric + "]";
        }
    }

    /** 사용자·날짜의 모든 행(소스·지표·시간대 순). 동의 여부는 호출자가 거른다. */
    public List<Row> findByUserIdAndLocalDate(Long userId, LocalDate localDate) {
        return jdbc.query("""
                        SELECT source, metric_key, value, value_at, unit, hour_bucket FROM daily_signals
                        WHERE user_id = ? AND local_date = ?
                        ORDER BY source, metric_key, hour_bucket NULLS FIRST
                        """,
                (rs, rowNum) -> signalRow(rs),
                userId, localDate);
    }

    public record DatedRow(LocalDate localDate, Row signal) {}

    public List<DatedRow> findMinuteHistory(Long userId, LocalDate from, LocalDate before) {
        return jdbc.query("""
                        SELECT source, metric_key, value, value_at, unit, hour_bucket, local_date FROM daily_signals
                        WHERE user_id = ? AND local_date >= ? AND local_date < ? AND unit = 'min'
                        ORDER BY local_date, source, metric_key, hour_bucket NULLS FIRST
                        """,
                (rs, rowNum) -> new DatedRow(rs.getObject(7, LocalDate.class), signalRow(rs)),
                userId, from, before);
    }

    private static Row signalRow(ResultSet rs) throws SQLException {
        return new Row(DataSource.valueOf(rs.getString(1)), Metric.valueOf(rs.getString(2)),
                rs.getBigDecimal(3), rs.getObject(4, OffsetDateTime.class), rs.getString(5),
                rs.getObject(6, Integer.class));
    }

    /**
     * (userId, source) 단위 트랜잭션 잠금(커밋·롤백 시 해제). 동의 철회와 수신을 직렬화해서,
     * 철회가 커밋되기 전에 동의 확인을 통과한 수신이 철회 후 삭제를 피해 데이터를 남기지 못하게 한다.
     */
    public void lockSource(Long userId, DataSource source) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                "daily_signals:" + userId + ":" + source.name());
    }

    public int deleteByUserIdAndSource(Long userId, DataSource source) {
        return jdbc.update("DELETE FROM daily_signals WHERE user_id = ? AND source = ?", userId, source.name());
    }
}
