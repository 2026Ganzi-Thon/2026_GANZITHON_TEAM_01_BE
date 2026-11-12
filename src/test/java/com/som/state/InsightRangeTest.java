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

/** 마이 화면 7/30일 기록(A_SPEC §10·§13). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class InsightRangeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @AfterEach
    void cleanUp() {
        clock.reset();
        ApiTestSupport.deleteUsers(jdbc, "inr");
    }

    private MvcResult insights(MockHttpSession session, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/insights" + query).session(session)).andReturn();
    }

    @Test
    void days_7과_30은_오늘이_마지막인_N일을_돌려준다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "inr01");

        JsonNode seven = json(objectMapper, insights(s, "?days=7"));
        assertThat(seven.path("days")).hasSize(7);
        assertThat(seven.path("from").asText()).isEqualTo("2026-11-05");
        assertThat(seven.path("to").asText()).isEqualTo("2026-11-11");
        assertThat(seven.path("days").get(6).path("localDate").asText()).isEqualTo("2026-11-11");

        JsonNode thirty = json(objectMapper, insights(s, "?days=30"));
        assertThat(thirty.path("days")).hasSize(30);
        assertThat(thirty.path("from").asText()).isEqualTo("2026-10-13");
    }

    @Test
    void 허용되지_않은_days는_400() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "inr02");
        for (String query : new String[] {"?days=14", "?days=abc", "", "?days=0", "?days=-7"}) {
            MvcResult result = insights(s, query);
            assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400);
            assertThat(json(objectMapper, result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void 기록이_없는_날은_null이고_기록이_없으면_평균도_null() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "inr03");

        JsonNode body = json(objectMapper, insights(s, "?days=7"));

        assertThat(body.path("averageLevel").isNull()).isTrue();
        assertThat(body.path("recordedDays").asInt()).isZero();
        body.path("days").forEach(d -> assertThat(d.path("level").isNull()).isTrue());
    }

    @Test
    void 다른_사용자의_기록은_섞이지_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "inr04a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "inr04b");
        mockMvc.perform(post("/api/v1/checkins").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mood\":\"TIRED\"}")).andReturn();

        assertThat(json(objectMapper, insights(a, "?days=7")).path("recordedDays").asInt()).isEqualTo(1);
        assertThat(json(objectMapper, insights(b, "?days=7")).path("recordedDays").asInt()).isZero();
    }
}
