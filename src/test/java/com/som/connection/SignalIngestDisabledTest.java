package com.som.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

/** som.signals.ingest-enabled=false(운영 프로필)면 수신 API가 없는 것처럼 404. 철회 시 삭제 연결은 그대로 동작한다. */
@SpringBootTest(properties = "som.signals.ingest-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignalIngestDisabledTest {

    static final String PASSWORD = "abcde12345";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        String ids = "(SELECT id FROM users WHERE login_id LIKE 'sd%')";
        jdbc.update("DELETE FROM daily_signals WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM connections WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM user_consents WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'sd%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'sd%'");
    }

    @Test
    void 수신이_꺼져_있으면_404_NOT_FOUND() throws Exception {
        MockHttpSession session = new MockHttpSession();
        assertThat(mockMvc.perform(post("/api/v1/auth/signup").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("loginId", "sdoff01", "password", PASSWORD, "passwordConfirm", PASSWORD))))
                .andReturn().getResponse().getStatus()).isEqualTo(201);
        List<Map<String, Object>> items = List.of("AGE_14_PLUS", "TERMS_OF_SERVICE", "PRIVACY_COLLECTION").stream()
                .map(key -> Map.<String, Object>of("key", key, "granted", true)).toList();
        mockMvc.perform(patch("/api/v1/me/consents").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("consents", items))));
        mockMvc.perform(post("/api/v1/me/connections/STEPS").session(session).with(csrf()));

        MvcResult result = mockMvc.perform(post("/api/v1/signals").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"STEPS\",\"localDate\":\"2020-01-01\","
                        + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":1}]}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("error").path("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_signals WHERE user_id = "
                + "(SELECT id FROM users WHERE login_id = 'sdoff01')", Integer.class)).isZero();
        // 철회 경로는 수신 설정과 무관하게 동작한다
        assertThat(mockMvc.perform(delete("/api/v1/me/connections/STEPS").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
