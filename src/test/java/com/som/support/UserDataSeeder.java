package com.som.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** 삭제 테스트용: 한 사용자가 모든 기능 테이블에 행을 갖게 API로 만든다(오늘 = MutableClock.DEFAULT). */
public final class UserDataSeeder {

    /** 사용자 소유 데이터가 있는 테이블(삭제 검증 대상). */
    public static final List<String> USER_TABLES = List.of("checkins", "daily_signals", "fatigue_snapshots",
            "routine_sessions", "routine_recommendations", "journal_entries", "wool_trim_events", "chat_messages",
            "recommendation_responses", "idempotency_keys", "connections", "notification_settings", "user_consents");

    private UserDataSeeder() {}

    public static MockHttpSession seedAll(MockMvc mockMvc, ObjectMapper objectMapper, String loginId) throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
        ok(mockMvc.perform(post("/api/v1/checkins").session(s).with(csrf()).header("Idempotency-Key", "seed-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mood\":\"TIRED\"}")).andReturn().getResponse()
                .getStatus());
        ok(mockMvc.perform(post("/api/v1/me/connections/STEPS").session(s).with(csrf())).andReturn().getResponse()
                .getStatus());
        ok(mockMvc.perform(post("/api/v1/signals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"STEPS\",\"localDate\":\"2026-11-11\","
                        + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":1200}]}")).andReturn().getResponse()
                .getStatus());
        JsonNode routines = objectMapper.readTree(mockMvc.perform(get("/api/v1/routines/today").session(s))
                .andReturn().getResponse().getContentAsString()).path("routines");
        for (JsonNode r : routines) {
            ok(mockMvc.perform(post("/api/v1/routines/" + r.path("routineId").asText() + "/complete").session(s)
                    .with(csrf())).andReturn().getResponse().getStatus());
        }
        ok(mockMvc.perform(post("/api/v1/journal").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotion\":\"TIRED\",\"text\":\"seed\"}")).andReturn().getResponse().getStatus());
        JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/v1/chat/messages?date=2026-11-11").session(s))
                .andReturn().getResponse().getContentAsString()).path("items");
        for (JsonNode item : items) {
            if (item.path("messageType").asText().equals("ROUTINE_RECOMMENDATION")) {
                ok(mockMvc.perform(post("/api/v1/chat/recommendations/" + item.path("id").asLong() + "/response")
                        .session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}")).andReturn().getResponse().getStatus());
            }
        }
        ok(mockMvc.perform(post("/api/v1/chat/messages").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "seed")))).andReturn().getResponse()
                .getStatus());
        ok(mockMvc.perform(patch("/api/v1/me/notification-settings").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andReturn().getResponse()
                .getStatus());
        return s;
    }

    public static int rows(JdbcTemplate jdbc, String table, Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    public static Long userId(JdbcTemplate jdbc, String loginId) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private static void ok(int status) {
        assertThat(status).isBetween(200, 201);
    }
}
