package com.som.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignalIngestTest {

    static final Instant FIXED = Instant.parse("2026-11-11T14:00:00Z"); // KST 2026-11-11 23:00
    static final String SIGNALS = "/api/v1/signals";
    static final String CONNECTIONS = "/api/v1/me/connections";
    static final String PASSWORD = "abcde12345";
    static final String TODAY = "2026-11-11";

    static final String SLEEP_BODY = """
            {"source":"SLEEP","localDate":"2026-11-11","entries":[
              {"metric":"SLEEP_ONSET","valueAt":"2026-11-11T00:30:00+09:00"},
              {"metric":"SLEEP_WAKE","valueAt":"2026-11-11T07:10:00+09:00"}]}
            """;

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

    @AfterEach
    void cleanUp() {
        String ids = "(SELECT id FROM users WHERE login_id LIKE 'sg%')";
        jdbc.update("DELETE FROM daily_signals WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM connections WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM user_consents WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'sg%'");
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

    private void connect(MockHttpSession session, String source) throws Exception {
        assertThat(mockMvc.perform(post(CONNECTIONS + "/" + source).session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private void revoke(MockHttpSession session, String source) throws Exception {
        assertThat(mockMvc.perform(delete(CONNECTIONS + "/" + source).session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult ingest(MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(post(SIGNALS).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String body(String source, String localDate, String... entries) {
        return "{\"source\":\"" + source + "\",\"localDate\":\"" + localDate + "\",\"entries\":["
                + String.join(",", entries) + "]}";
    }

    private List<Map<String, Object>> rows(Long userId) {
        return jdbc.queryForList(
                "SELECT * FROM daily_signals WHERE user_id = ? ORDER BY metric_key, hour_bucket", userId);
    }

    private Map<String, Object> row(Long userId, String metric) {
        return jdbc.queryForMap(
                "SELECT * FROM daily_signals WHERE user_id = ? AND metric_key = ? AND hour_bucket IS NULL",
                userId, metric);
    }

    private int count(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM daily_signals WHERE user_id = ?", Integer.class, userId);
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json(result).path("error").path("code").asText()).isEqualTo(code);
    }

    @Test
    void SLEEP_연결후_수신하면_ONSET_WAKE_파생MINUTES_3행() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgsleep01");
        Long userId = userId("sgsleep01");
        connect(session, "SLEEP");

        MvcResult result = ingest(session, SLEEP_BODY);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("source").asText()).isEqualTo("SLEEP");
        assertThat(body.path("localDate").asText()).isEqualTo(TODAY);
        assertThat(body.path("stored").asInt()).isEqualTo(3);

        assertThat(rows(userId)).extracting(r -> r.get("metric_key"))
                .containsExactly("SLEEP_MINUTES", "SLEEP_ONSET", "SLEEP_WAKE");
        for (Map<String, Object> r : rows(userId)) {
            assertThat(r.get("source")).isEqualTo("SLEEP");
            assertThat(r.get("provider")).isEqualTo("DEMO");
            assertThat(r.get("local_date").toString()).isEqualTo(TODAY);
            assertThat(r.get("hour_bucket")).isNull();
            assertThat(((Timestamp) r.get("collected_at")).toInstant()).isEqualTo(FIXED);
        }
        Map<String, Object> minutes = row(userId, "SLEEP_MINUTES");
        assertThat((BigDecimal) minutes.get("value")).isEqualByComparingTo("400");
        assertThat(minutes.get("unit")).isEqualTo("min");
        assertThat(minutes.get("value_at")).isNull();

        Map<String, Object> onset = row(userId, "SLEEP_ONSET");
        assertThat(((Timestamp) onset.get("value_at")).toInstant()).isEqualTo(Instant.parse("2026-11-10T15:30:00Z"));
        assertThat(onset.get("value")).isNull();
        assertThat(onset.get("unit")).isNull();
    }

    @Test
    void 연결하지_않은_STEPS_수신은_403_SOURCE_NOT_CONSENTED_0행() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgnocon01");
        connect(session, "SLEEP");

        MvcResult result = ingest(session, body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":8000}"));

        assertError(result, 403, "SOURCE_NOT_CONSENTED");
        assertThat(count(userId("sgnocon01"))).isZero();
    }

    @Test
    void 통합시나리오5_연결_수신_철회하면_삭제되고_재수신은_403() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgscen01");
        Long userId = userId("sgscen01");
        connect(session, "SLEEP");
        connect(session, "STEPS");
        assertThat(ingest(session, SLEEP_BODY).getResponse().getStatus()).isEqualTo(200);
        assertThat(ingest(session, body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":8000}"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(count(userId)).isEqualTo(4);

        revoke(session, "SLEEP");

        assertThat(rows(userId)).extracting(r -> r.get("source")).containsExactly("STEPS");
        assertError(ingest(session, SLEEP_BODY), 403, "SOURCE_NOT_CONSENTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_signals WHERE user_id = ? AND source = 'SLEEP'", Integer.class, userId))
                .isZero();
    }

    @Test
    void SCREEN_TIME_hourBucket_누락이나_24는_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgscrn01");
        Long userId = userId("sgscrn01");
        connect(session, "SCREEN_TIME");

        for (String entry : List.of("{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":30}",
                "{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":30,\"hourBucket\":24}",
                "{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":30,\"hourBucket\":-1}")) {
            assertError(ingest(session, body("SCREEN_TIME", TODAY, entry)), 400, "VALIDATION_ERROR");
        }
        assertThat(count(userId)).isZero();

        MvcResult ok = ingest(session, body("SCREEN_TIME", TODAY,
                "{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":30,\"hourBucket\":0}",
                "{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":45,\"hourBucket\":23}"));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ok).path("stored").asInt()).isEqualTo(2);
        assertThat(rows(userId)).extracting(r -> ((Number) r.get("hour_bucket")).intValue()).containsExactly(0, 23);
        assertThat(rows(userId)).extracting(r -> r.get("unit")).containsOnly("min");
    }

    @Test
    void hourBucket이_없는_지표에_hourBucket을_보내면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sghour01");
        connect(session, "STEPS");

        assertError(ingest(session, body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":10,\"hourBucket\":3}")),
                400, "VALIDATION_ERROR");
        assertThat(count(userId("sghour01"))).isZero();
    }

    @Test
    void STEPS에_SLEEP_ONSET_지표가_섞이면_400_부분저장_없음() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgmix01");
        connect(session, "STEPS");
        connect(session, "SLEEP");

        MvcResult result = ingest(session, body("STEPS", TODAY,
                "{\"metric\":\"STEPS_DAILY\",\"value\":8000}",
                "{\"metric\":\"SLEEP_ONSET\",\"valueAt\":\"2026-11-11T00:30:00+09:00\"}"));

        assertError(result, 400, "VALIDATION_ERROR");
        assertThat(count(userId("sgmix01"))).isZero();
    }

    @Test
    void 클라이언트가_SLEEP_MINUTES를_직접_보내면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgmin01");
        connect(session, "SLEEP");

        assertError(ingest(session, body("SLEEP", TODAY, "{\"metric\":\"SLEEP_MINUTES\",\"value\":480}")),
                400, "VALIDATION_ERROR");
        assertThat(count(userId("sgmin01"))).isZero();
    }

    @Test
    void 값_종류가_틀리면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgkind01");
        connect(session, "SLEEP");
        connect(session, "STEPS");

        for (String body : List.of(
                body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\"}"),
                body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"valueAt\":\"2026-11-11T07:00:00+09:00\"}"),
                body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":1,\"valueAt\":\"2026-11-11T07:00:00+09:00\"}"),
                body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":-1}"),
                body("SLEEP", TODAY, "{\"metric\":\"SLEEP_ONSET\",\"value\":30}"),
                body("STEPS", TODAY, "{\"metric\":\"UNKNOWN_METRIC\",\"value\":1}"),
                body("STEPS", TODAY))) {
            assertError(ingest(session, body), 400, "VALIDATION_ERROR");
        }
        assertThat(count(userId("sgkind01"))).isZero();
    }

    @Test
    void 깬_시각이_잠든_시각보다_빠르면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgorder01");
        connect(session, "SLEEP");

        MvcResult result = ingest(session, body("SLEEP", TODAY,
                "{\"metric\":\"SLEEP_ONSET\",\"valueAt\":\"2026-11-11T07:10:00+09:00\"}",
                "{\"metric\":\"SLEEP_WAKE\",\"valueAt\":\"2026-11-11T00:30:00+09:00\"}"));

        assertError(result, 400, "VALIDATION_ERROR");
        assertThat(count(userId("sgorder01"))).isZero();
    }

    @Test
    void 한_요청에_같은_키가_두번이면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgdup01");
        connect(session, "STEPS");

        assertError(ingest(session, body("STEPS", TODAY,
                "{\"metric\":\"STEPS_DAILY\",\"value\":1}", "{\"metric\":\"STEPS_DAILY\",\"value\":2}")),
                400, "VALIDATION_ERROR");
        assertThat(count(userId("sgdup01"))).isZero();
    }

    @Test
    void 안보는_것_필드는_400이고_본문에_값이_없다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgfield01");
        connect(session, "CALENDAR_PERSONAL");

        for (String body : List.of(
                body("CALENDAR_PERSONAL", TODAY, "{\"metric\":\"CALENDAR_EVENT_COUNT\",\"value\":3,\"title\":\"회의\"}"),
                "{\"source\":\"CALENDAR_PERSONAL\",\"localDate\":\"2026-11-11\",\"location\":\"강남역\","
                        + "\"entries\":[{\"metric\":\"CALENDAR_EVENT_COUNT\",\"value\":3}]}",
                body("CALENDAR_PERSONAL", TODAY, "{\"metric\":\"CALENDAR_BUSY_MINUTES\",\"value\":3,\"appName\":\"카톡\"}"),
                "{\"source\":\"CALENDAR_PERSONAL\",\"localDate\":\"2026-11-11\",\"userId\":1,"
                        + "\"entries\":[{\"metric\":\"CALENDAR_EVENT_COUNT\",\"value\":3}]}")) {
            MvcResult result = ingest(session, body);
            assertError(result, 400, "VALIDATION_ERROR");
            String content = result.getResponse().getContentAsString();
            assertThat(content).doesNotContain("회의", "강남역", "카톡");
        }
        assertThat(count(userId("sgfield01"))).isZero();
    }

    @Test
    void 미래_localDate는_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgfut01");
        connect(session, "STEPS");

        assertError(ingest(session, body("STEPS", "2026-11-12", "{\"metric\":\"STEPS_DAILY\",\"value\":1}")),
                400, "VALIDATION_ERROR");
        assertThat(count(userId("sgfut01"))).isZero();
    }

    @Test
    void entries_상한을_넘으면_400() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgmax01");
        connect(session, "SCREEN_TIME");
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            entries.add("{\"metric\":\"SCREEN_TIME_MINUTES\",\"value\":1,\"hourBucket\":" + (i % 24) + "}");
        }

        assertError(ingest(session, body("SCREEN_TIME", TODAY, entries.toArray(String[]::new))),
                400, "VALIDATION_ERROR");
        assertThat(count(userId("sgmax01"))).isZero();
    }

    @Test
    void 같은_키를_다시_보내면_행수는_그대로이고_값이_갱신된다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgups01");
        Long userId = userId("sgups01");
        connect(session, "STEPS");
        connect(session, "SLEEP");
        ingest(session, body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":8000}"));
        ingest(session, SLEEP_BODY);
        assertThat(count(userId)).isEqualTo(4);

        assertThat(ingest(session, body("STEPS", TODAY, "{\"metric\":\"STEPS_DAILY\",\"value\":9500}"))
                .getResponse().getStatus()).isEqualTo(200);
        // 깬 시각만 다시 보내도 저장된 잠든 시각으로 수면 분을 다시 파생한다
        MvcResult wake = ingest(session, body("SLEEP", TODAY,
                "{\"metric\":\"SLEEP_WAKE\",\"valueAt\":\"2026-11-11T08:30:00+09:00\"}"));
        assertThat(wake.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(wake).path("stored").asInt()).isEqualTo(2);

        assertThat(count(userId)).isEqualTo(4);
        assertThat((BigDecimal) row(userId, "STEPS_DAILY").get("value")).isEqualByComparingTo("9500");
        assertThat(row(userId, "STEPS_DAILY").get("unit")).isEqualTo("count");
        assertThat((BigDecimal) row(userId, "SLEEP_MINUTES").get("value")).isEqualByComparingTo("480");
    }

    @Test
    void 잠든_시각만_있으면_수면분은_만들지_않는다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgonly01");
        Long userId = userId("sgonly01");
        connect(session, "SLEEP");

        MvcResult result = ingest(session, body("SLEEP", TODAY,
                "{\"metric\":\"SLEEP_ONSET\",\"valueAt\":\"2026-11-11T00:30:00+09:00\"}"));

        assertThat(json(result).path("stored").asInt()).isEqualTo(1);
        assertThat(rows(userId)).extracting(r -> r.get("metric_key")).containsExactly("SLEEP_ONSET");
    }

    @Test
    void 캘린더_지표는_count와_min_단위로_저장된다() throws Exception {
        MockHttpSession session = signupWithRequiredConsents("sgcal01");
        Long userId = userId("sgcal01");
        connect(session, "CALENDAR_WORK");

        MvcResult result = ingest(session, body("CALENDAR_WORK", TODAY,
                "{\"metric\":\"CALENDAR_EVENT_COUNT\",\"value\":4}",
                "{\"metric\":\"CALENDAR_BUSY_MINUTES\",\"value\":210}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> units = new LinkedHashMap<>();
        rows(userId).forEach(r -> units.put((String) r.get("metric_key"), r.get("unit")));
        assertThat(units).containsExactly(Map.entry("CALENDAR_BUSY_MINUTES", "min"),
                Map.entry("CALENDAR_EVENT_COUNT", "count"));
    }

    @Test
    void 다른_사용자_수신과_철회는_서로_영향이_없다() throws Exception {
        MockHttpSession sessionA = signupWithRequiredConsents("sgusra01");
        MockHttpSession sessionB = signupWithRequiredConsents("sgusrb01");
        Long userA = userId("sgusra01");
        Long userB = userId("sgusrb01");
        connect(sessionA, "SLEEP");
        assertThat(ingest(sessionA, SLEEP_BODY).getResponse().getStatus()).isEqualTo(200);

        // A가 연결했어도 B는 미연결 → 403
        assertError(ingest(sessionB, SLEEP_BODY), 403, "SOURCE_NOT_CONSENTED");
        assertThat(count(userB)).isZero();

        // B가 연결·수신 후 철회해도 A의 행은 그대로
        connect(sessionB, "SLEEP");
        assertThat(ingest(sessionB, SLEEP_BODY).getResponse().getStatus()).isEqualTo(200);
        revoke(sessionB, "SLEEP");
        assertThat(count(userB)).isZero();
        assertThat(count(userA)).isEqualTo(3);

        // A가 철회해도 B의 행은 그대로
        connect(sessionB, "SLEEP");
        ingest(sessionB, SLEEP_BODY);
        revoke(sessionA, "SLEEP");
        assertThat(count(userA)).isZero();
        assertThat(count(userB)).isEqualTo(3);
    }

    @Test
    void 필수_동의_미완료면_403_CONSENT_REQUIRED() throws Exception {
        MockHttpSession session = signup("sggate01");

        assertError(ingest(session, SLEEP_BODY), 403, "CONSENT_REQUIRED");
        assertThat(count(userId("sggate01"))).isZero();
    }

    @Test
    void 미인증은_401_CSRF_없으면_403() throws Exception {
        assertThat(mockMvc.perform(post(SIGNALS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SLEEP_BODY)).andReturn().getResponse().getStatus()).isEqualTo(401);

        MockHttpSession session = signupWithRequiredConsents("sgcsrf01");
        connect(session, "SLEEP");
        assertThat(mockMvc.perform(post(SIGNALS).session(session).contentType(MediaType.APPLICATION_JSON)
                .content(SLEEP_BODY)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(count(userId("sgcsrf01"))).isZero();
    }
}
