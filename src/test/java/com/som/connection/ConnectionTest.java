package com.som.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.repository.UserRepository;
import com.som.connection.domain.DataSource;
import com.som.connection.service.SourceConsentChecker;
import com.som.connection.service.SourceRevokedEvent;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ConnectionTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00
    static final String CONNECTIONS = "/api/v1/me/connections";
    static final String PASSWORD = "abcde12345";
    static final List<String> SOURCES =
            List.of("SLEEP", "STEPS", "SCREEN_TIME", "CALENDAR_PERSONAL", "CALENDAR_WORK");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SourceConsentChecker checker;
    @Autowired ApplicationEvents events;

    @AfterEach
    void cleanUp() {
        // 테스트 정리용 삭제. 애플리케이션 코드에는 user_consents DELETE가 없다.
        jdbc.update("DELETE FROM connections WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'cn%')");
        jdbc.update("DELETE FROM user_consents WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'cn%')");
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'cn%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'cn%'");
    }

    private MockHttpSession signup(String loginId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String json = objectMapper.writeValueAsString(
                Map.of("loginId", loginId, "password", PASSWORD, "passwordConfirm", PASSWORD));
        int status = mockMvc.perform(post("/api/v1/auth/signup").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(201);
        return session;
    }

    private MockHttpSession signupWithRequiredConsents(String loginId) throws Exception {
        MockHttpSession session = signup(loginId);
        List<Map<String, Object>> items = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION").stream()
                .map(key -> Map.<String, Object>of("key", key, "granted", true)).toList();
        int status = mockMvc.perform(patch("/api/v1/me/consents").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consents", items))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
        return session;
    }

    private Long userId(String loginId) {
        return users.findByLoginId(loginId).orElseThrow().getId();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode list(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(CONNECTIONS).session(session)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(result);
    }

    private JsonNode item(JsonNode list, String source) {
        for (JsonNode node : list) {
            if (node.path("source").asText().equals(source)) {
                return node;
            }
        }
        throw new AssertionError("missing source " + source);
    }

    private MvcResult connect(MockHttpSession session, String source) throws Exception {
        return mockMvc.perform(post(CONNECTIONS + "/" + source).session(session).with(csrf())).andReturn();
    }

    private MvcResult revoke(MockHttpSession session, String source) throws Exception {
        return mockMvc.perform(delete(CONNECTIONS + "/" + source).session(session).with(csrf())).andReturn();
    }

    private List<Map<String, Object>> sourceConsentRows(Long userId) {
        return jdbc.queryForList("""
                SELECT consent_type, source, items, purpose, retention, is_granted, policy_version, changed_at
                FROM user_consents WHERE user_id = ? AND consent_type = 'DATA_SOURCE' ORDER BY id
                """, userId);
    }

    private Map<String, Object> connectionRow(Long userId, String source) {
        return jdbc.queryForMap("SELECT * FROM connections WHERE user_id = ? AND source = ?", userId, source);
    }

    private long revokedEvents() {
        return events.stream(SourceRevokedEvent.class).count();
    }

    @Test
    void 신규_사용자는_5개_모두_NOT_CONNECTED이고_SLEEP은_sensitive() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnnew01");

        JsonNode body = list(session);

        assertThat(body).hasSize(5);
        for (String source : SOURCES) {
            JsonNode item = item(body, source);
            assertThat(item.path("status").asText()).isEqualTo("NOT_CONNECTED");
            assertThat(item.path("connectedAt").isNull()).isTrue();
            assertThat(item.path("policyVersion").asText()).isEqualTo("v1");
            assertThat(item.path("items").isArray()).isTrue();
            assertThat(item.has("purpose")).isTrue();
            assertThat(item.has("retention")).isTrue();
        }
        JsonNode sleep = item(body, "SLEEP");
        assertThat(sleep.path("sensitive").asBoolean()).isTrue();
        assertThat(objectMapper.convertValue(sleep.path("items"), List.class)).containsExactly("잠든 시각", "깬 시각");
        assertThat(sleep.path("purpose").asText()).isEqualTo("평소와 달라졌는지 살피는 데만 써요");
        assertThat(item(body, "STEPS").path("sensitive").asBoolean()).isFalse();
        assertThat(checker.consentedSources(userId("cnnew01"))).isEmpty();
        assertThat(sourceConsentRows(userId("cnnew01"))).isEmpty();
    }

    @Test
    void SLEEP_연결하면_CONNECTED_동의이력에_서버_설정값이_기록된다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnconn01");
        Long userId = userId("cnconn01");

        MvcResult result = connect(session, "SLEEP");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("source").asText()).isEqualTo("SLEEP");
        assertThat(body.path("status").asText()).isEqualTo("CONNECTED");
        assertThat(body.path("sensitive").asBoolean()).isTrue();
        assertThat(body.path("connectedAt").asText()).isEqualTo("2026-11-07T23:00:00+09:00");

        List<Map<String, Object>> rows = sourceConsentRows(userId);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("source")).isEqualTo("SLEEP");
        assertThat(row.get("is_granted")).isEqualTo(true);
        assertThat(row.get("policy_version")).isEqualTo("v1");
        assertThat(objectMapper.readValue((String) row.get("items"), List.class)).containsExactly("잠든 시각", "깬 시각");
        assertThat(row.get("purpose")).isEqualTo("평소와 달라졌는지 살피는 데만 써요");
        assertThat(row.get("retention")).isEqualTo("TBD");
        assertThat(((Timestamp) row.get("changed_at")).toInstant()).isEqualTo(FIXED);

        Map<String, Object> conn = connectionRow(userId, "SLEEP");
        assertThat(conn.get("status")).isEqualTo("CONNECTED");
        assertThat(conn.get("provider")).isEqualTo("DEMO");
        assertThat(((Timestamp) conn.get("connected_at")).toInstant()).isEqualTo(FIXED);
        assertThat(conn.get("revoked_at")).isNull();

        assertThat(checker.isConsented(userId, DataSource.SLEEP)).isTrue();
        assertThat(checker.isConsented(userId, DataSource.STEPS)).isFalse();
        assertThat(checker.consentedSources(userId)).containsExactly(DataSource.SLEEP);
        assertThat(item(list(session), "SLEEP").path("status").asText()).isEqualTo("CONNECTED");
    }

    @Test
    void 철회하면_REVOKED_이력2행_isConsented_false_철회이벤트_발행() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnrevk01");
        Long userId = userId("cnrevk01");
        connect(session, "SLEEP");

        MvcResult result = revoke(session, "SLEEP");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("status").asText()).isEqualTo("REVOKED");
        assertThat(body.path("connectedAt").isNull()).isTrue();

        List<Map<String, Object>> rows = sourceConsentRows(userId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("is_granted")).isEqualTo(true);
        assertThat(rows.get(1).get("is_granted")).isEqualTo(false);
        assertThat(rows.get(1).get("policy_version")).isEqualTo("v1");

        Map<String, Object> conn = connectionRow(userId, "SLEEP");
        assertThat(conn.get("status")).isEqualTo("REVOKED");
        assertThat(((Timestamp) conn.get("revoked_at")).toInstant()).isEqualTo(FIXED);

        assertThat(checker.isConsented(userId, DataSource.SLEEP)).isFalse();
        assertThat(checker.consentedSources(userId)).isEmpty();
        assertThat(events.stream(SourceRevokedEvent.class).toList())
                .containsExactly(new SourceRevokedEvent(userId, DataSource.SLEEP));
    }

    @Test
    void 재연결하면_이력3행_CONNECTED() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnreco01");
        Long userId = userId("cnreco01");
        connect(session, "STEPS");
        revoke(session, "STEPS");

        MvcResult result = connect(session, "STEPS");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).path("status").asText()).isEqualTo("CONNECTED");
        assertThat(sourceConsentRows(userId)).extracting(r -> r.get("is_granted")).containsExactly(true, false, true);
        Map<String, Object> conn = connectionRow(userId, "STEPS");
        assertThat(conn.get("status")).isEqualTo("CONNECTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(checker.isConsented(userId, DataSource.STEPS)).isTrue();
    }

    @Test
    void 같은_요청을_반복하면_이력이_늘지_않는다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnidem01");
        Long userId = userId("cnidem01");

        // 이력 없음 + 철회 → 행도 이벤트도 없음
        MvcResult first = revoke(session, "SLEEP");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(first).path("status").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(sourceConsentRows(userId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE user_id = ?", Integer.class, userId))
                .isZero();

        connect(session, "SLEEP");
        assertThat(connect(session, "SLEEP").getResponse().getStatus()).isEqualTo(200);
        assertThat(sourceConsentRows(userId)).hasSize(1);

        revoke(session, "SLEEP");
        MvcResult again = revoke(session, "SLEEP");
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).path("status").asText()).isEqualTo("REVOKED");
        assertThat(sourceConsentRows(userId)).hasSize(2);
        assertThat(revokedEvents()).isEqualTo(1);
    }

    @Test
    void 바디에_고지내용이나_userId를_보내면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnbody01");
        Long userId = userId("cnbody01");

        for (String json : List.of("{\"items\":[\"일정 제목\"]}", "{\"retention\":\"forever\"}", "{\"userId\":1}")) {
            MvcResult result = mockMvc.perform(post(CONNECTIONS + "/SLEEP").session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
            assertThat(result.getResponse().getStatus()).as(json).isEqualTo(400);
            assertThat(json(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        }
        MvcResult del = mockMvc.perform(delete(CONNECTIONS + "/SLEEP").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1}")).andReturn();
        assertThat(del.getResponse().getStatus()).isEqualTo(400);

        assertThat(sourceConsentRows(userId)).isEmpty();
    }

    @Test
    void 모르는_source는_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnunkn01");

        for (MvcResult result : List.of(connect(session, "LOCATION"), revoke(session, "LOCATION"),
                connect(session, "sleep"))) {
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            JsonNode error = json(result).path("error");
            assertThat(error.path("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.path("message").asText()).doesNotContain("LOCATION");
        }
        assertThat(sourceConsentRows(userId("cnunkn01"))).isEmpty();
    }

    @Test
    void 선택_동의가_없어도_서비스API는_동작한다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cnopt01");

        MvcResult result = mockMvc.perform(get("/api/v1/test-service/ping").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(checker.consentedSources(userId("cnopt01"))).isEmpty();
    }

    @Test
    void 다른_사용자_A의_연결_철회는_B에_영향이_없다() throws Exception {
        MockHttpSession sessionA = signupWithRequiredConsents("cnusra01");
        MockHttpSession sessionB = signupWithRequiredConsents("cnusrb01");
        Long userA = userId("cnusra01");
        Long userB = userId("cnusrb01");
        connect(sessionB, "CALENDAR_WORK");

        connect(sessionA, "SLEEP");
        connect(sessionA, "CALENDAR_WORK");
        revoke(sessionA, "CALENDAR_WORK");

        JsonNode bodyB = list(sessionB);
        assertThat(item(bodyB, "SLEEP").path("status").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(item(bodyB, "CALENDAR_WORK").path("status").asText()).isEqualTo("CONNECTED");
        assertThat(sourceConsentRows(userB)).hasSize(1);
        assertThat(checker.consentedSources(userB)).isEqualTo(Set.of(DataSource.CALENDAR_WORK));

        JsonNode bodyA = list(sessionA);
        assertThat(item(bodyA, "SLEEP").path("status").asText()).isEqualTo("CONNECTED");
        assertThat(item(bodyA, "CALENDAR_WORK").path("status").asText()).isEqualTo("REVOKED");
        assertThat(checker.consentedSources(userA)).isEqualTo(Set.of(DataSource.SLEEP));

        // B가 철회해도 A는 그대로
        revoke(sessionB, "CALENDAR_WORK");
        assertThat(item(list(sessionA), "SLEEP").path("status").asText()).isEqualTo("CONNECTED");
        assertThat(sourceConsentRows(userA)).hasSize(3);
    }

    @Test
    void 필수_동의_미완료면_403_CONSENT_REQUIRED() throws Exception {
        MockHttpSession session = signup("cngate01");

        for (MvcResult result : List.of(
                mockMvc.perform(get(CONNECTIONS).session(session)).andReturn(),
                connect(session, "SLEEP"),
                revoke(session, "SLEEP"))) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(json(result).path("error").path("code").asText()).isEqualTo("CONSENT_REQUIRED");
        }
        assertThat(sourceConsentRows(userId("cngate01"))).isEmpty();
    }

    @Test
    void 미인증이면_401() throws Exception {
        assertThat(mockMvc.perform(get(CONNECTIONS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void CSRF_토큰_없이_연결하면_403() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("cncsrf01");

        MvcResult result = mockMvc.perform(post(CONNECTIONS + "/SLEEP").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(sourceConsentRows(userId("cncsrf01"))).isEmpty();
    }
}
