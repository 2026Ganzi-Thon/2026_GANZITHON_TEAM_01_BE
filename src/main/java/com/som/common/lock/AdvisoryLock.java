package com.som.common.lock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 트랜잭션 잠금(pg_advisory_xact_lock). 같은 키를 쓰는 트랜잭션끼리 커밋·롤백까지 직렬화한다.
 * 호출자는 반드시 트랜잭션 안에 있어야 한다(밖이면 문장 하나로 끝나 잠금 효과가 없다).
 */
@Component
public class AdvisoryLock {

    private final JdbcTemplate jdbc;

    public AdvisoryLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key);
    }
}
