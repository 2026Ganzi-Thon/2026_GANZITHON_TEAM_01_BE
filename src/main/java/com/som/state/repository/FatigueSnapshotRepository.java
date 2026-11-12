package com.som.state.repository;

import com.som.state.domain.FatigueSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 피로 스냅샷 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface FatigueSnapshotRepository extends Repository<FatigueSnapshot, Long> {

    @Modifying
    @Query(value = """
            UPDATE fatigue_snapshots
            SET signal_changes = COALESCE((
                SELECT cast(jsonb_agg(change) AS text)
                FROM jsonb_array_elements(cast(signal_changes AS jsonb)) AS changes(change)
                WHERE change ->> 'source' <> :source
            ), '[]')
            WHERE user_id = :userId AND signal_changes <> '[]'
            """, nativeQuery = true)
    void removeSignalChanges(@Param("userId") Long userId, @Param("source") String source);

    FatigueSnapshot save(FatigueSnapshot snapshot);

    Optional<FatigueSnapshot> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<FatigueSnapshot> findByUserIdAndLocalDateBetweenOrderByLocalDate(Long userId, LocalDate from, LocalDate to);
}
