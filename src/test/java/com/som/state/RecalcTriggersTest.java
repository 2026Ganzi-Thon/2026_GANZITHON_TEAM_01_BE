package com.som.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableFatigueEngine;
import com.som.support.TestSupportConfig;
import java.util.List;
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

/** 신호 수신·동의 철회 뒤 스냅샷 재계산(통합 시나리오 5: 수면 연결 → 철회 → 수집 거부·계산 제외). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RecalcTriggersTest {

    static final String SLEEP_BODY = """
            {"source":"SLEEP","localDate":"2026-11-11","entries":[
              {"metric":"SLEEP_ONSET","valueAt":"2026-11-11T01:00:00+09:00"},
              {"metric":"SLEEP_WAKE","valueAt":"2026-11-11T05:30:00+09:00"}]}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableFatigueEngine engine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "rct");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private int ingest(MockHttpSession s, String body) throws Exception {
        return status(post("/api/v1/signals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<Map<String, Object>> snapshots(String loginId) {
        return jdbc.queryForList("SELECT s.* FROM fatigue_snapshots s JOIN users u ON u.id = s.user_id "
                + "WHERE u.login_id = ? ORDER BY s.local_date", loginId);
    }

    @Test
    void 수면_수신_후_스냅샷에_SLEEP이_쓰이고_철회하면_다시_계산되어_빠진다() throws Exception {
        MockHttpSession s = ready("rct01");
        assertThat(status(post("/api/v1/me/connections/SLEEP").session(s).with(csrf()))).isEqualTo(200);
        status(post("/api/v1/checkins").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"OKAY\"}"));

        assertThat(ingest(s, SLEEP_BODY)).isEqualTo(200);
        assertThat((String) snapshots("rct01").get(0).get("used_sources")).contains("SLEEP");

        assertThat(status(delete("/api/v1/me/connections/SLEEP").session(s).with(csrf()))).isEqualTo(200);
        List<Map<String, Object>> after = snapshots("rct01");
        assertThat(after).hasSize(1);
        assertThat((String) after.get(0).get("used_sources")).doesNotContain("SLEEP");
        assertThat(engine.lastRequest().signals()).isEmpty();

        // 철회 후 수신은 거부되고 스냅샷은 그대로다
        Object calculatedAt = after.get(0).get("calculated_at");
        int calls = engine.requests().size();
        assertThat(ingest(s, SLEEP_BODY)).isEqualTo(403);
        assertThat(engine.requests()).hasSize(calls);
        assertThat(snapshots("rct01").get(0).get("calculated_at")).isEqualTo(calculatedAt);
    }

    @Test
    void 신호만_들어와도_그날_스냅샷이_만들어진다() throws Exception {
        MockHttpSession s = ready("rct02");
        status(post("/api/v1/me/connections/STEPS").session(s).with(csrf()));

        assertThat(ingest(s, "{\"source\":\"STEPS\",\"localDate\":\"2026-11-10\","
                + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":1200}]}")).isEqualTo(200);

        List<Map<String, Object>> rows = snapshots("rct02");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("local_date").toString()).isEqualTo("2026-11-10");
        assertThat(rows.get(0).get("status")).isEqualTo("CALCULATED");
    }

    @Test
    void engine_장애_중_수신은_성공하고_기존_스냅샷은_stale() throws Exception {
        MockHttpSession s = ready("rct03");
        status(post("/api/v1/me/connections/STEPS").session(s).with(csrf()));
        status(post("/api/v1/checkins").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"CALM\"}"));

        engine.fail(true);
        assertThat(ingest(s, "{\"source\":\"STEPS\",\"localDate\":\"2026-11-11\","
                + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":800}]}")).isEqualTo(200);

        assertThat(snapshots("rct03").get(0).get("is_stale")).isEqualTo(true);
    }

    @Test
    void 다른_사용자의_수신은_내_스냅샷을_바꾸지_않는다() throws Exception {
        MockHttpSession a = ready("rct04a");
        MockHttpSession b = ready("rct04b");
        status(post("/api/v1/checkins").session(b).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"CALM\"}"));
        Map<String, Object> before = snapshots("rct04b").get(0);
        status(post("/api/v1/me/connections/STEPS").session(a).with(csrf()));

        ingest(a, "{\"source\":\"STEPS\",\"localDate\":\"2026-11-11\","
                + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":800}]}");

        assertThat(snapshots("rct04b").get(0)).isEqualTo(before);
    }
}
