package com.som.state.repository;

import com.som.state.domain.FatigueSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 피로 스냅샷 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface FatigueSnapshotRepository extends Repository<FatigueSnapshot, Long> {

    FatigueSnapshot save(FatigueSnapshot snapshot);

    Optional<FatigueSnapshot> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<FatigueSnapshot> findByUserIdAndLocalDateBetweenOrderByLocalDate(Long userId, LocalDate from, LocalDate to);
}
