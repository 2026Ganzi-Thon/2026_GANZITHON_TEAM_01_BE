package com.som.connection.repository;

import com.som.connection.domain.Connection;
import com.som.connection.domain.DataSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 연결 상태 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface ConnectionRepository extends Repository<Connection, Long> {

    Connection save(Connection connection);

    Optional<Connection> findByUserIdAndSource(Long userId, DataSource source);

    List<Connection> findByUserId(Long userId);
}
