package com.som.state;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.engine.insight.InsightRequest;
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

/** 이번 주(월~일, KST) 변화. 평균·요약은 InsightEngine 결과 그대로(A_SPEC §7·§12). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class InsightWeekTest {

    static final String WEEK = "/api/v1/insights/week";

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
        ApiTestSupport.deleteUsers(jdbc, "inw");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private void submitAt(MockHttpSession session, String instant, String body) throws Exception {
        clock.set(instant);
        mockMvc.perform(post("/api/v1/checkins").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult week(MockHttpSession session) throws Exception {
        return mockMvc.perform(get(WEEK).session(session)).andReturn();
    }

    @Test
    void 이번_주_7일을_돌려주고_기록일만_단계가_있다() throws Exception {
        MockHttpSession s = ready("inw01");
        submitAt(s, "2026-11-09T05:00:00Z", "{\"mood\":\"CALM\"}");                          // 월
        submitAt(s, "2026-11-10T05:00:00Z", "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}"); // 화
        clock.set("2026-11-11T05:00:00Z");                                                    // 수 조회

        JsonNode body = json(objectMapper, week(s));

        assertThat(body.path("from").asText()).isEqualTo("2026-11-09");
        assertThat(body.path("to").asText()).isEqualTo("2026-11-15");
        JsonNode days = body.path("days");
        assertThat(days).hasSize(7);
        assertThat(days.get(0).path("localDate").asText()).isEqualTo("2026-11-09");
        assertThat(days.get(0).path("level").isNull()).isFalse();
        assertThat(days.get(1).path("level").isNull()).isFalse();
        for (int i = 2; i < 7; i++) {
            assertThat(days.get(i).path("level").isNull()).as("day " + i).isTrue();
            assertThat(days.get(i).path("woolDisplayKg").isNull()).isTrue();
        }
        assertThat(body.path("recordedDays").asInt()).isEqualTo(2);
        double expected = (days.get(0).path("level").asInt() + days.get(1).path("level").asInt()) / 2.0;
        assertThat(body.path("averageLevel").asDouble()).isEqualTo(Math.round(expected * 10) / 10.0);
    }

    @Test
    void 기록이_없으면_평균은_null이고_0이_아니다() throws Exception {
        MockHttpSession s = ready("inw02");

        JsonNode body = json(objectMapper, week(s));

        assertThat(body.path("days")).hasSize(7);
        assertThat(body.path("recordedDays").asInt()).isZero();
        assertThat(body.path("averageLevel").isNull()).isTrue();
        assertThat(body.path("heaviestDate").isNull()).isTrue();
        assertThat(body.path("woolWeightKg").isNull()).isTrue();
    }

    @Test
    void 일요일_밤과_월요일_0시에_주가_바뀐다() throws Exception {
        MockHttpSession s = ready("inw03");

        clock.set("2026-11-15T14:59:59Z"); // KST 일 23:59:59
        assertThat(json(objectMapper, week(s)).path("from").asText()).isEqualTo("2026-11-09");
        clock.set("2026-11-15T15:00:00Z"); // KST 월 00:00
        assertThat(json(objectMapper, week(s)).path("from").asText()).isEqualTo("2026-11-16");
    }

    @Test
    void 요약_engine이_실패하면_503() throws Exception {
        MockHttpSession s = ready("inw04");
        insightEngine.fail(true);

        MvcResult result = week(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(json(objectMapper, result).path("error").path("code").asText()).isEqualTo("ENGINE_UNAVAILABLE");
    }

    @Test
    void 다른_사용자의_기록은_섞이지_않고_요청에_가명_ID만_간다() throws Exception {
        MockHttpSession a = ready("inw05a");
        MockHttpSession b = ready("inw05b");
        submitAt(a, "2026-11-09T05:00:00Z", "{\"mood\":\"TIRED\"}");
        clock.set("2026-11-11T05:00:00Z");

        JsonNode body = json(objectMapper, week(b));

        assertThat(body.path("recordedDays").asInt()).isZero();
        InsightRequest request = insightEngine.lastRequest();
        String bKey = jdbc.queryForObject("SELECT subject_key FROM users WHERE login_id = 'inw05b'", String.class);
        assertThat(request.subjectKey()).isEqualTo(bKey);
        assertThat(request.days()).hasSize(7).allSatisfy(d -> assertThat(d.level()).isNull());
    }

    @Test
    void 필수_약관_미동의는_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "inw06");
        assertThat(week(s).getResponse().getStatus()).isEqualTo(403);
    }
}
