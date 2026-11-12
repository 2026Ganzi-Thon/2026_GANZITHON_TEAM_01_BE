package com.som.state;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableFatigueEngine;
import com.som.support.TestSupportConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class StateTodayTest {

    static final String TODAY_STATE = "/api/v1/state/today";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableFatigueEngine engine;
    @Value("${som.state.bubble-text.insufficient}") String insufficientBubble;
    @Value("${som.state.bubble-text.levels[0]}") String level1Bubble;

    @AfterEach
    void cleanUp() {
        clock.reset();
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "stt");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private void submit(MockHttpSession session, String body) throws Exception {
        mockMvc.perform(post("/api/v1/checkins").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult today(MockHttpSession session) throws Exception {
        return mockMvc.perform(get(TODAY_STATE).session(session)).andReturn();
    }

    private Map<String, Object> snapshot(String loginId) {
        return jdbc.queryForMap("SELECT s.* FROM fatigue_snapshots s JOIN users u ON u.id = s.user_id "
                + "WHERE u.login_id = ?", loginId);
    }

    @Test
    void 체크인_후_조회하면_스냅샷_값을_그대로_보여준다() throws Exception {
        MockHttpSession s = ready("stt01");
        submit(s, "{\"mood\":\"CALM\"}");

        MvcResult result = today(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(objectMapper, result);
        Map<String, Object> row = snapshot("stt01");
        assertThat(body.path("localDate").asText()).isEqualTo("2026-11-11");
        assertThat(body.path("status").asText()).isEqualTo("CALCULATED");
        assertThat(body.path("score").asInt()).isEqualTo(((Number) row.get("score")).intValue());
        assertThat(body.path("level").asInt()).isEqualTo(((Number) row.get("level")).intValue());
        assertThat(body.path("statusText").asText()).isEqualTo(row.get("status_text"));
        assertThat(body.path("usedSources").get(0).asText()).isEqualTo("CHECKIN");
        assertThat(body.path("algorithmVersion").asText()).isEqualTo("mock-0.1");
        assertThat(body.path("calculatedAt").asText()).endsWith("+09:00");
        assertThat(body.path("isStale").asBoolean()).isFalse();
        assertThat(body.path("woolTrimmedToday").asBoolean()).isFalse();
        assertThat(body.path("bubbleText").asText()).isNotBlank();
        assertThat(body.has("userId")).isFalse();
        assertThat(body.has("subjectKey")).isFalse();
    }

    @Test
    void 아무_입력이_없으면_INSUFFICIENT_DATA이고_점수_단계는_null() throws Exception {
        MockHttpSession s = ready("stt02");

        JsonNode body = json(objectMapper, today(s));

        assertThat(body.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(body.path("score").isNull()).isTrue();
        assertThat(body.path("level").isNull()).isTrue();
        assertThat(body.path("woolDisplayKg").isNull()).isTrue();
        assertThat(body.path("bubbleText").asText()).isEqualTo(insufficientBubble);
    }

    @Test
    void engine이_실패하면_마지막_정상_값과_isStale_true() throws Exception {
        MockHttpSession s = ready("stt03");
        submit(s, "{\"mood\":\"CALM\"}");
        int level = json(objectMapper, today(s)).path("level").asInt();

        engine.fail(true);
        submit(s, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");
        JsonNode body = json(objectMapper, today(s));

        assertThat(body.path("level").asInt()).isEqualTo(level);
        assertThat(body.path("isStale").asBoolean()).isTrue();
    }

    @Test
    void engine이_실패하고_스냅샷도_없으면_503() throws Exception {
        MockHttpSession s = ready("stt04");
        engine.fail(true);

        MvcResult result = today(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        JsonNode error = json(objectMapper, result).path("error");
        assertThat(error.path("code").asText()).isEqualTo("ENGINE_UNAVAILABLE");
        assertThat(error.has("requestId")).isTrue();
    }

    @Test
    void 단계가_있으면_단계별_말풍선_문구() throws Exception {
        MockHttpSession s = ready("stt05");
        submit(s, "{\"mood\":\"CALM\"}"); // Mock: CALM 단독 = 1단계

        JsonNode body = json(objectMapper, today(s));

        assertThat(body.path("level").asInt()).isEqualTo(1);
        assertThat(body.path("bubbleText").asText()).isEqualTo(level1Bubble);
    }

    @Test
    void 필수_약관_미동의는_403_미인증은_401() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "stt06");
        assertThat(today(s).getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get(TODAY_STATE)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void 다른_사용자의_스냅샷은_보이지_않는다() throws Exception {
        MockHttpSession a = ready("stt07a");
        MockHttpSession b = ready("stt07b");
        submit(a, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");

        JsonNode body = json(objectMapper, today(b));

        assertThat(body.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(body.path("level").isNull()).isTrue();
    }
}
