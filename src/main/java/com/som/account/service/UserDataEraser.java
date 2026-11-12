package com.som.account.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 소유 데이터 연쇄 삭제(A_SPEC §11). 한 트랜잭션, 모든 DELETE는 user_id 조건 — 다른 사용자 행은 건드리지 않는다.
 * 순서는 FK 기준(참조하는 테이블부터). 새 사용자 테이블을 추가하면 여기에 넣어야 한다(UserDataEraserTest 안전망).
 */
@Component
public class UserDataEraser {

    /** 기록(DATA) 삭제 대상. 계정 설정성 데이터(connections·notification_settings·user_consents)는 남긴다. */
    private static final List<String> DATA_TABLES = List.of(
            "recommendation_responses", "chat_messages", "wool_trim_events", "journal_entries", "routine_sessions",
            "routine_recommendations", "fatigue_snapshots", "daily_signals", "idempotency_keys", "checkins");

    // TODO(DECISION): 탈퇴 시 동의 이력 보존 여부(A_SPEC §14) — 기본: 삭제(users FK). 보존하려면 익명화 테이블로 옮겨야 한다
    private static final List<String> ACCOUNT_ONLY_TABLES = List.of(
            "connections", "notification_settings", "user_consents");

    private final JdbcTemplate jdbc;

    public UserDataEraser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 삭제 범위에 들어가는 user_id 테이블(ACCOUNT는 users 포함). */
    public static List<String> tablesFor(DeletionScope scope) {
        List<String> tables = new ArrayList<>(DATA_TABLES);
        if (scope == DeletionScope.ACCOUNT) {
            tables.addAll(ACCOUNT_ONLY_TABLES);
            tables.add("users");
        }
        return tables;
    }

    /** @return 테이블별 지운 행 수(로그·응답용 — 값은 담지 않는다) */
    @Transactional
    public Map<String, Integer> erase(Long userId, DeletionScope scope) {
        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("checkin_stressors", jdbc.update(
                "DELETE FROM checkin_stressors WHERE checkin_id IN (SELECT id FROM checkins WHERE user_id = ?)", userId));
        for (String table : tablesFor(scope)) {
            String column = table.equals("users") ? "id" : "user_id";
            deleted.put(table, jdbc.update("DELETE FROM " + table + " WHERE " + column + " = ?", userId));
        }
        return deleted;
    }
}
