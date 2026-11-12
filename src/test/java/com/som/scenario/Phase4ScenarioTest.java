package com.som.scenario;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** A_SPEC §15 통합 시나리오 5: 수면 연결 → 철회 → 수집 거부·계산 제외 → 전체 삭제 → 재조회 불가(+ 탈퇴). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class Phase4ScenarioTest {

    static final String SLEEP = """
            {"source":"SLEEP","localDate":"2026-11-11","entries":[
              {"metric":"SLEEP_ONSET","valueAt":"2026-11-11T01:00:00+09:00"},
              {"metric":"SLEEP_WAKE","valueAt":"2026-11-11T06:00:00+09:00"}]}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "sc4");
        jdbc.update("DELETE FROM deletion_requests");
    }

    private int status(MockHttpSession s, MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req.session(s)).andReturn().getResponse().getStatus();
    }

    private JsonNode body(MockHttpSession s, MockHttpServletRequestBuilder req) throws Exception {
        return json(objectMapper, mockMvc.perform(req.session(s)).andReturn());
    }

    @Test
    void 시나리오5_연결_철회_수집거부_전체삭제_재조회불가_탈퇴() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "sc401");
        assertThat(status(s, post("/api/v1/checkins").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"TIRED\"}"))).isEqualTo(201);
        assertThat(status(s, post("/api/v1/me/connections/SLEEP").with(csrf()))).isEqualTo(200);
        assertThat(status(s, post("/api/v1/signals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SLEEP))).isEqualTo(200);
        assertThat(body(s, get("/api/v1/state/today")).path("usedSources").toString()).contains("SLEEP");

        // 철회 → 수집 거부, 계산에서 제외
        assertThat(status(s, delete("/api/v1/me/connections/SLEEP").with(csrf()))).isEqualTo(200);
        assertThat(status(s, post("/api/v1/signals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SLEEP))).isEqualTo(403);
        assertThat(body(s, get("/api/v1/state/today")).path("usedSources").toString()).doesNotContain("SLEEP");

        // 대화·기록을 더 만든 뒤 전체 기록 삭제
        mockMvc.perform(post("/api/v1/chat/messages").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "삭제 전 대화")))).andReturn();
        JsonNode deleted = body(s, delete("/api/v1/me/data").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":true}"));
        assertThat(deleted.path("status").asText()).isEqualTo("COMPLETED");

        // 재조회 불가
        assertThat(status(s, get("/api/v1/checkins?date=2026-11-11"))).isEqualTo(404);
        assertThat(body(s, get("/api/v1/chat/dates?month=2026-11"))).isEmpty();
        assertThat(body(s, get("/api/v1/insights?days=7")).path("recordedDays").asInt()).isZero();
        assertThat(body(s, get("/api/v1/state/today")).path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_signals d JOIN users u ON u.id = d.user_id "
                + "WHERE u.login_id = 'sc401'", Integer.class)).isZero();

        // 탈퇴 → 로그인 불가
        assertThat(status(s, delete("/api/v1/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":true}"))).isEqualTo(200);
        assertThat(mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", "sc401", "password",
                        ApiTestSupport.PASSWORD)))).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
