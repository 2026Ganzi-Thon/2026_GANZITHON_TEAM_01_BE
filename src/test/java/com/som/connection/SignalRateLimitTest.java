package com.som.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 연동 데이터 수신 요청 제한(세션 사용자 기준, ADR-008). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "som.rate-limit.signals.per-user.capacity=2",
        "som.rate-limit.signals.per-user.refill-period=1h"})
class SignalRateLimitTest {

    static final String PASSWORD = "abcde12345";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceDates dates;

    @AfterEach
    void cleanUp() {
        String ids = "(SELECT id FROM users WHERE login_id LIKE 'sgrl%')";
        jdbc.update("DELETE FROM daily_signals WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM connections WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM user_consents WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'sgrl%'");
    }

    private MockHttpSession readyUser(String loginId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        assertThat(mockMvc.perform(post("/api/v1/auth/signup").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("loginId", loginId, "password", PASSWORD, "passwordConfirm", PASSWORD))))
                .andReturn().getResponse().getStatus()).isEqualTo(201);
        List<Map<String, Object>> items = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION").stream()
                .map(key -> Map.<String, Object>of("key", key, "granted", true)).toList();
        assertThat(mockMvc.perform(patch("/api/v1/me/consents").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consents", items))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(post("/api/v1/me/connections/STEPS").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        return session;
    }

    private MockHttpServletResponse ingest(MockHttpSession session) throws Exception {
        String body = "{\"source\":\"STEPS\",\"localDate\":\"" + dates.today()
                + "\",\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":1000}]}";
        return mockMvc.perform(post("/api/v1/signals").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    @Test
    void 같은_사용자가_capacity를_넘으면_429이고_다른_사용자는_영향받지_않는다() throws Exception {
        MockHttpSession a = readyUser("sgrla01");
        MockHttpSession b = readyUser("sgrlb01");
        assertThat(ingest(a).getStatus()).isEqualTo(200);
        assertThat(ingest(a).getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = ingest(a);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        JsonNode json = objectMapper.readTree(limited.getContentAsString());
        assertThat(json.path("error").path("code").asText()).isEqualTo(ErrorCode.RATE_LIMITED.name());
        assertThat(ingest(b).getStatus()).isEqualTo(200);
    }
}
