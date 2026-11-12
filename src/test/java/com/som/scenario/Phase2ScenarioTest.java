package com.som.scenario;

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
import com.som.support.SwitchableInsightEngine;
import com.som.support.TestSupportConfig;
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
import org.springframework.test.web.servlet.MvcResult;

/** A_SPEC §15 통합 시나리오 1·2 + 결측·격리 확인(phase 2 범위). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class Phase2ScenarioTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableFatigueEngine fatigueEngine;
    @Autowired SwitchableInsightEngine insightEngine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        fatigueEngine.reset();
        insightEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "scn");
    }

    private MvcResult checkin(MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/checkins").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult call(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session)).andReturn();
    }

    @Test
    void 시나리오1_가입_후_약관_미동의_체크인은_403_동의_후_성공하고_오늘_상태가_계산된다() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "scn01");

        MvcResult denied = checkin(s, "{\"mood\":\"TIRED\"}");
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(objectMapper, denied).path("error").path("code").asText()).isEqualTo("CONSENT_REQUIRED");

        ApiTestSupport.grantRequiredConsents(mockMvc, objectMapper, s);
        assertThat(checkin(s, "{\"mood\":\"TIRED\"}").getResponse().getStatus()).isEqualTo(201);

        JsonNode today = json(objectMapper, call(s, "/api/v1/state/today"));
        assertThat(today.path("status").asText()).isEqualTo("CALCULATED");
        assertThat(today.path("level").asInt()).isBetween(1, 4);
    }

    @Test
    void 시나리오2_체크인_재제출_갱신과_engine_장애시_마지막_값_isStale_그리고_이번주_503() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "scn02");

        checkin(s, "{\"mood\":\"CALM\"}");
        JsonNode first = json(objectMapper, call(s, "/api/v1/state/today"));
        JsonNode week = json(objectMapper, call(s, "/api/v1/insights/week"));
        assertThat(week.path("recordedDays").asInt()).isEqualTo(1);
        assertThat(week.path("days").get(2).path("level").asInt()).isEqualTo(first.path("level").asInt()); // 수요일

        checkin(s, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");
        JsonNode second = json(objectMapper, call(s, "/api/v1/state/today"));
        assertThat(second.path("score").asInt()).isGreaterThan(first.path("score").asInt());
        assertThat(second.path("isStale").asBoolean()).isFalse();

        fatigueEngine.fail(true);
        insightEngine.fail(true);
        assertThat(checkin(s, "{\"mood\":\"CALM\"}").getResponse().getStatus()).isEqualTo(200);
        JsonNode stale = json(objectMapper, call(s, "/api/v1/state/today"));
        assertThat(stale.path("score").asInt()).isEqualTo(second.path("score").asInt());
        assertThat(stale.path("isStale").asBoolean()).isTrue();
        assertThat(call(s, "/api/v1/insights/week").getResponse().getStatus()).isEqualTo(503);
    }

    @Test
    void 아무_입력이_없는_신규_사용자는_오늘_INSUFFICIENT_DATA이고_이번주는_모두_null() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "scn03");

        JsonNode today = json(objectMapper, call(s, "/api/v1/state/today"));
        assertThat(today.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(today.path("score").isNull()).isTrue();
        assertThat(today.path("level").isNull()).isTrue();

        JsonNode week = json(objectMapper, call(s, "/api/v1/insights/week"));
        assertThat(week.path("days")).hasSize(7);
        week.path("days").forEach(d -> assertThat(d.path("level").isNull()).isTrue());
        assertThat(week.path("averageLevel").isNull()).isTrue();
        assertThat(week.path("recordedDays").asInt()).isZero();
    }

    @Test
    void 두_사용자의_체크인_상태_기록은_서로_섞이지_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "scn04a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "scn04b");
        checkin(a, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");
        checkin(b, "{\"mood\":\"CALM\"}");

        assertThat(json(objectMapper, call(a, "/api/v1/checkins?date=2026-11-11")).path("mood").asText())
                .isEqualTo("TIRED");
        assertThat(json(objectMapper, call(b, "/api/v1/checkins?date=2026-11-11")).path("mood").asText())
                .isEqualTo("CALM");
        int aScore = json(objectMapper, call(a, "/api/v1/state/today")).path("score").asInt();
        int bScore = json(objectMapper, call(b, "/api/v1/state/today")).path("score").asInt();
        assertThat(aScore).isNotEqualTo(bScore);
        assertThat(json(objectMapper, call(a, "/api/v1/insights?days=7")).path("recordedDays").asInt()).isEqualTo(1);
        assertThat(json(objectMapper, call(b, "/api/v1/insights?days=7")).path("recordedDays").asInt()).isEqualTo(1);
    }
}
