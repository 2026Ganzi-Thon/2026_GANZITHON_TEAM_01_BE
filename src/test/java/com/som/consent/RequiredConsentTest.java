package com.som.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.repository.UserRepository;
import com.som.consent.service.RequiredConsentChecker;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class RequiredConsentTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00
    static final String CONSENTS = "/api/v1/me/consents";
    static final String PASSWORD = "abcde12345";
    static final List<String> KEYS = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION");

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
    @Autowired RequiredConsentChecker checker;

    @AfterEach
    void cleanUp() {
        // 테스트 정리용 삭제. 애플리케이션 코드에는 user_consents DELETE가 없다.
        jdbc.update("DELETE FROM user_consents WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'rc%')");
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'rc%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'rc%'");
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

    private Long userId(String loginId) {
        return users.findByLoginId(loginId).orElseThrow().getId();
    }

    private String patchBody(Object... keyGrantedPairs) throws Exception {
        List<Map<String, Object>> consents = new ArrayList<>();
        for (int i = 0; i < keyGrantedPairs.length; i += 2) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", keyGrantedPairs[i]);
            item.put("granted", keyGrantedPairs[i + 1]);
            consents.add(item);
        }
        return objectMapper.writeValueAsString(Map.of("consents", consents));
    }

    private String grantAll() throws Exception {
        return patchBody("AGE_14_PLUS", true, "TERMS_OF_SERVICE", true, "PRIVACY_COLLECTION", true);
    }

    private MvcResult patchConsents(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(patch(CONSENTS).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private JsonNode getConsents(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(CONSENTS).session(session)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(result);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode item(JsonNode body, String key) {
        for (JsonNode node : body.path("required")) {
            if (node.path("key").asText().equals(key)) {
                return node;
            }
        }
        throw new AssertionError("missing key " + key);
    }

    private int rowCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_consents WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void 신규_사용자는_3개_모두_미동의이고_requiredCompleted_false() throws Exception {
        MockHttpSession session = signup("rcnew01");

        JsonNode body = getConsents(session);

        assertThat(body.path("required")).hasSize(3);
        for (String key : KEYS) {
            JsonNode item = item(body, key);
            assertThat(item.path("granted").asBoolean()).isFalse();
            assertThat(item.path("policyVersion").isNull()).isTrue();
            assertThat(item.path("changedAt").isNull()).isTrue();
        }
        assertThat(body.path("requiredCompleted").asBoolean()).isFalse();
        assertThat(checker.isCompleted(userId("rcnew01"))).isFalse();
        assertThat(rowCount(userId("rcnew01"))).isZero();
    }

    @Test
    void 세개_동의하면_완료되고_서버_정책버전과_Clock_시각으로_3행이_쌓인다() throws Exception {
        MockHttpSession session = signup("rcall01");

        MvcResult result = patchConsents(session, grantAll());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("requiredCompleted").asBoolean()).isTrue();
        for (String key : KEYS) {
            JsonNode item = item(body, key);
            assertThat(item.path("granted").asBoolean()).isTrue();
            assertThat(item.path("policyVersion").asText()).isEqualTo("v1");
            assertThat(OffsetDateTime.parse(item.path("changedAt").asText()).toInstant()).isEqualTo(FIXED);
        }
        assertThat(getConsents(session)).isEqualTo(body);

        Long userId = userId("rcall01");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT consent_type, source, is_granted, policy_version, changed_at FROM user_consents WHERE user_id = ?",
                userId);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("consent_type")).containsExactlyInAnyOrderElementsOf(KEYS);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("source")).isNull();
            assertThat(row.get("is_granted")).isEqualTo(true);
            assertThat(row.get("policy_version")).isEqualTo("v1");
            assertThat(((Timestamp) row.get("changed_at")).toInstant()).isEqualTo(FIXED);
        }
        assertThat(checker.isCompleted(userId)).isTrue();
    }

    @Test
    void 같은_요청을_반복하면_행이_늘지_않는다() throws Exception {
        MockHttpSession session = signup("rcidem01");
        patchConsents(session, grantAll());

        MvcResult again = patchConsents(session, grantAll());

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).path("requiredCompleted").asBoolean()).isTrue();
        assertThat(rowCount(userId("rcidem01"))).isEqualTo(3);
    }

    @Test
    void 이력이_없는데_미동의를_보내면_행을_만들지_않는다() throws Exception {
        MockHttpSession session = signup("rcfalse1");

        MvcResult result = patchConsents(session, patchBody("AGE_14_PLUS", false));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(rowCount(userId("rcfalse1"))).isZero();
    }

    @Test
    void 하나를_철회하면_기존_행은_그대로_두고_새_행이_추가된다() throws Exception {
        MockHttpSession session = signup("rcrev01");
        patchConsents(session, grantAll());
        Long userId = userId("rcrev01");
        String select = "SELECT id, consent_type, is_granted FROM user_consents WHERE user_id = ? ORDER BY id";
        List<Map<String, Object>> before = jdbc.queryForList(select, userId);

        MvcResult result = patchConsents(session, patchBody("TERMS_OF_SERVICE", false));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("requiredCompleted").asBoolean()).isFalse();
        assertThat(item(body, "TERMS_OF_SERVICE").path("granted").asBoolean()).isFalse();
        assertThat(item(body, "AGE_14_PLUS").path("granted").asBoolean()).isTrue();
        assertThat(getConsents(session).path("requiredCompleted").asBoolean()).isFalse();
        assertThat(checker.isCompleted(userId)).isFalse();

        List<Map<String, Object>> after = jdbc.queryForList(select, userId);
        assertThat(after).hasSize(4);
        assertThat(after.subList(0, 3)).isEqualTo(before);
        assertThat(after.get(3).get("consent_type")).isEqualTo("TERMS_OF_SERVICE");
        assertThat(after.get(3).get("is_granted")).isEqualTo(false);

        // 다시 동의하면 또 새 행 (같은 changed_at이어도 가장 최근 행이 현재 상태)
        patchConsents(session, patchBody("TERMS_OF_SERVICE", true));
        assertThat(rowCount(userId)).isEqualTo(5);
        assertThat(checker.isCompleted(userId)).isTrue();
    }

    @Test
    void 모르는_key는_400_VALIDATION_ERROR() throws Exception {
        MockHttpSession session = signup("rcbad01");

        MvcResult result = patchConsents(session, patchBody("MARKETING", true));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("MARKETING");
        assertThat(rowCount(userId("rcbad01"))).isZero();
    }

    @Test
    void 클라이언트가_보낸_policyVersion_changedAt은_거부된다() throws Exception {
        MockHttpSession session = signup("rcforge1");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", "AGE_14_PLUS");
        item.put("granted", true);
        item.put("policyVersion", "v999");
        item.put("changedAt", "2020-01-01T00:00:00+09:00");

        MvcResult result = patchConsents(session, objectMapper.writeValueAsString(Map.of("consents", List.of(item))));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(rowCount(userId("rcforge1"))).isZero();
    }

    @Test
    void granted가_없거나_consents가_비면_400() throws Exception {
        MockHttpSession session = signup("rcnull01");
        Map<String, Object> noGranted = new LinkedHashMap<>();
        noGranted.put("key", "AGE_14_PLUS");

        assertThat(patchConsents(session, objectMapper.writeValueAsString(Map.of("consents", List.of(noGranted))))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(patchConsents(session, "{\"consents\":[]}").getResponse().getStatus()).isEqualTo(400);
        assertThat(patchConsents(session, "{}").getResponse().getStatus()).isEqualTo(400);
        assertThat(rowCount(userId("rcnull01"))).isZero();
    }

    @Test
    void 한_요청에_같은_key가_중복되면_400() throws Exception {
        MockHttpSession session = signup("rcdup01");

        MvcResult result = patchConsents(session, patchBody("AGE_14_PLUS", true, "AGE_14_PLUS", false));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(rowCount(userId("rcdup01"))).isZero();
    }

    @Test
    void 다른_사용자의_동의_상태는_서로_영향을_주지_않는다() throws Exception {
        MockHttpSession a = signup("rcusera");
        MockHttpSession b = signup("rcuserb");

        patchConsents(a, grantAll());

        JsonNode bView = getConsents(b);
        assertThat(bView.path("requiredCompleted").asBoolean()).isFalse();
        for (String key : KEYS) {
            assertThat(item(bView, key).path("granted").asBoolean()).isFalse();
        }
        assertThat(rowCount(userId("rcuserb"))).isZero();
        assertThat(checker.isCompleted(userId("rcuserb"))).isFalse();

        // B의 변경도 A에 영향 없음
        patchConsents(b, patchBody("AGE_14_PLUS", true));
        patchConsents(b, patchBody("AGE_14_PLUS", false));
        assertThat(getConsents(a).path("requiredCompleted").asBoolean()).isTrue();
        assertThat(rowCount(userId("rcusera"))).isEqualTo(3);
        assertThat(rowCount(userId("rcuserb"))).isEqualTo(2);
    }

    @Test
    void 바디에_userId를_넣으면_거부되고_누구의_행도_생기지_않는다() throws Exception {
        MockHttpSession a = signup("rcuida1");
        signup("rcuidb1");
        Long bId = userId("rcuidb1");

        MvcResult result = patchConsents(a, "{\"userId\":" + bId
                + ",\"consents\":[{\"key\":\"AGE_14_PLUS\",\"granted\":true}]}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(rowCount(bId)).isZero();
        assertThat(rowCount(userId("rcuida1"))).isZero();
    }

    @Test
    void 미인증이면_401() throws Exception {
        assertThat(mockMvc.perform(get(CONSENTS)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(patch(CONSENTS).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(grantAll()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        MockHttpSession session = signup("rccsrf01");

        MvcResult result = mockMvc.perform(patch(CONSENTS).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(grantAll())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(rowCount(userId("rccsrf01"))).isZero();
    }
}
