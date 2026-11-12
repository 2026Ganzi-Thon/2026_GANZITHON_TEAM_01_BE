package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequiredConsentGateTest {

    static final String PING = "/api/v1/test-service/ping";
    static final String CONSENTS = "/api/v1/me/consents";
    static final String PASSWORD = "abcde12345";
    static final List<String> KEYS = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_consents WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'gate%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'gate%'");
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

    private int setConsents(MockHttpSession session, Map<String, Boolean> values) throws Exception {
        List<Map<String, Object>> items = values.entrySet().stream()
                .map(e -> Map.<String, Object>of("key", e.getKey(), "granted", e.getValue())).toList();
        String json = objectMapper.writeValueAsString(Map.of("consents", items));
        return mockMvc.perform(patch(CONSENTS).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
    }

    private void grantAll(MockHttpSession session) throws Exception {
        assertThat(setConsents(session, Map.of(KEYS.get(0), true, KEYS.get(1), true, KEYS.get(2), true)))
                .isEqualTo(200);
    }

    private MvcResult ping(MockHttpSession session) throws Exception {
        return mockMvc.perform(get(PING).session(session).header("X-Request-ID", "gate-req-1")).andReturn();
    }

    private void assertConsentRequired(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
        assertThat(error.path("code").asText()).isEqualTo("CONSENT_REQUIRED");
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("requestId").asText()).isEqualTo("gate-req-1");
    }

    @Test
    void 통합시나리오1_가입_후_서비스API_403_동의_후_200() throws Exception {
        MockHttpSession session = signup("gateflow1");

        assertConsentRequired(ping(session));

        grantAll(session);

        assertThat(ping(session).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 동의_전에도_계정_약관_API는_동작한다() throws Exception {
        MockHttpSession session = signup("gateexem1");

        assertThat(mockMvc.perform(get("/api/v1/me").session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mockMvc.perform(get(CONSENTS).session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(setConsents(session, Map.of(KEYS.get(0), true))).isEqualTo(200);
        assertThat(mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void 세개_중_두개만_동의하면_403() throws Exception {
        MockHttpSession session = signup("gatepart1");
        assertThat(setConsents(session, Map.of(KEYS.get(0), true, KEYS.get(1), true))).isEqualTo(200);

        assertConsentRequired(ping(session));
    }

    @Test
    void 동의_후_하나_철회하면_바로_다음_요청부터_403() throws Exception {
        MockHttpSession session = signup("gaterevk1");
        grantAll(session);
        assertThat(ping(session).getResponse().getStatus()).isEqualTo(200);

        assertThat(setConsents(session, Map.of(KEYS.get(2), false))).isEqualTo(200);

        assertConsentRequired(ping(session));
    }

    @Test
    void 미인증이면_401() throws Exception {
        MvcResult result = mockMvc.perform(get(PING)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("error").path("code").asText())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 다른_사용자_B는_동의완료_A는_미완료면_A만_403() throws Exception {
        MockHttpSession sessionA = signup("gateusra1");
        MockHttpSession sessionB = signup("gateusrb1");
        grantAll(sessionB);

        assertConsentRequired(ping(sessionA));
        assertThat(ping(sessionB).getResponse().getStatus()).isEqualTo(200);
    }
}
