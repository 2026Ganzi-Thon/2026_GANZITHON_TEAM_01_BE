package com.som.checkin.repository;

import com.som.checkin.domain.Checkin;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 체크인 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface CheckinRepository extends Repository<Checkin, Long> {

    Checkin save(Checkin checkin);

    Checkin saveAndFlush(Checkin checkin);

    Optional<Checkin> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<Checkin> findByUserIdAndLocalDateBetweenOrderByLocalDate(Long userId, LocalDate from, LocalDate to);
}
