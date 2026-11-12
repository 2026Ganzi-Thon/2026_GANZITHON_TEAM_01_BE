package com.som.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.web.servlet.MvcResult;

/** API 테스트 공용 도우미: 가입·필수 동의·사용자 데이터 정리. */
public final class ApiTestSupport {

    public static final String PASSWORD = "abcde12345";

    private ApiTestSupport() {}

    public static MockHttpSession signup(MockMvc mockMvc, ObjectMapper objectMapper, String loginId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        int status = mockMvc.perform(post("/api/v1/auth/signup").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("loginId", loginId, "password", PASSWORD, "passwordConfirm", PASSWORD))))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("signup " + loginId).isEqualTo(201);
        return session;
    }

    public static MockHttpSession signupWithRequiredConsents(MockMvc mockMvc, ObjectMapper objectMapper,
                                                             String loginId) throws Exception {
        MockHttpSession session = signup(mockMvc, objectMapper, loginId);
        grantRequiredConsents(mockMvc, objectMapper, session);
        return session;
    }

    public static void grantRequiredConsents(MockMvc mockMvc, ObjectMapper objectMapper, MockHttpSession session)
            throws Exception {
        List<Map<String, Object>> items = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION").stream()
                .map(key -> Map.<String, Object>of("key", key, "granted", true)).toList();
        int status = mockMvc.perform(patch("/api/v1/me/consents").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consents", items))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    public static JsonNode json(ObjectMapper objectMapper, MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** login_id가 prefix로 시작하는 사용자와 그 사용자의 모든 데이터를 지운다. */
    public static void deleteUsers(JdbcTemplate jdbc, String prefix) {
        String ids = "(SELECT id FROM users WHERE login_id LIKE '" + prefix + "%')";
        // FK 순서: 참조하는 테이블부터. 아직 없는 테이블은 건너뛴다.
        for (String table : List.of("recommendation_responses", "chat_messages", "wool_trim_events",
                "journal_entries", "routine_sessions", "routine_recommendations", "notification_settings",
                "fatigue_snapshots", "idempotency_keys", "daily_signals", "connections", "user_consents")) {
            if (tableExists(jdbc, table)) {
                jdbc.update("DELETE FROM " + table + " WHERE user_id IN " + ids);
            }
        }
        jdbc.update("DELETE FROM checkin_stressors WHERE checkin_id IN (SELECT id FROM checkins WHERE user_id IN "
                + ids + ")");
        jdbc.update("DELETE FROM checkins WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM users WHERE login_id LIKE '" + prefix + "%'");
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }
}
