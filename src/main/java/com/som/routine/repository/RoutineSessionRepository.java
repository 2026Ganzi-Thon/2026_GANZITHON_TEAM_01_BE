package com.som.routine.repository;

import com.som.routine.domain.RoutineSession;
import com.som.routine.domain.RoutineSessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 루틴 실행 기록 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface RoutineSessionRepository extends Repository<RoutineSession, Long> {

    RoutineSession saveAndFlush(RoutineSession session);

    Optional<RoutineSession> findByUserIdAndRoutineIdAndLocalDate(Long userId, String routineId, LocalDate localDate);

    List<RoutineSession> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<RoutineSession> findByUserIdAndLocalDateBetween(Long userId, LocalDate from, LocalDate to);

    List<RoutineSession> findByUserIdAndStatusAndLocalDateBetweenOrderByLocalDate(
            Long userId, RoutineSessionStatus status, LocalDate from, LocalDate to);
}
