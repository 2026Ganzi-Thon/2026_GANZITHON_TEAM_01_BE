package com.som.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.SignalInput;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableFatigueEngine;
import com.som.support.TestSupportConfig;
import java.time.LocalDate;
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

/** 체크인 → engine 계산 → 스냅샷 저장(A_SPEC §12, 통합 시나리오 2). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class SnapshotTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableFatigueEngine engine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "snp");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private int submit(MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/checkins").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    private Long userId(String loginId) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private List<Map<String, Object>> snapshots(String loginId) {
        return jdbc.queryForList("SELECT * FROM fatigue_snapshots WHERE user_id = ? ORDER BY local_date",
                userId(loginId));
    }

    @Test
    void 체크인을_제출하면_오늘_스냅샷이_engine_결과로_저장된다() throws Exception {
        MockHttpSession s = ready("snp01");

        assertThat(submit(s, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}")).isEqualTo(201);

        List<Map<String, Object>> rows = snapshots("snp01");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("local_date").toString()).isEqualTo(TODAY.toString());
        assertThat(row.get("status")).isEqualTo("CALCULATED");
        assertThat(((Number) row.get("level")).intValue()).isBetween(1, 4);
        assertThat(row.get("algorithm_version")).isEqualTo("mock-0.1");
        assertThat(row.get("is_stale")).isEqualTo(false);
        assertThat(row.get("used_sources")).isEqualTo("[\"CHECKIN\"]");
    }

    @Test
    void 모두_건너뛴_체크인이고_신호가_없으면_INSUFFICIENT_DATA이고_점수와_단계는_null() throws Exception {
        MockHttpSession s = ready("snp02");

        submit(s, "{}");

        Map<String, Object> row = snapshots("snp02").get(0);
        assertThat(row.get("status")).isEqualTo("INSUFFICIENT_DATA");
        assertThat(row.get("score")).isNull();
        assertThat(row.get("level")).isNull();
        assertThat(row.get("wool_display_kg")).isNull();
    }

    @Test
    void 재제출하면_스냅샷은_한_행이고_값이_바뀐다() throws Exception {
        MockHttpSession s = ready("snp03");
        submit(s, "{\"mood\":\"CALM\"}");
        int before = ((Number) snapshots("snp03").get(0).get("score")).intValue();

        submit(s, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");

        List<Map<String, Object>> rows = snapshots("snp03");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("score")).intValue()).isGreaterThan(before);
    }

    @Test
    void engine이_실패하면_체크인은_성공하고_기존_스냅샷은_값을_유지한_채_stale이_된다() throws Exception {
        MockHttpSession s = ready("snp04");
        submit(s, "{\"mood\":\"CALM\"}");
        Map<String, Object> before = snapshots("snp04").get(0);

        engine.fail(true);
        assertThat(submit(s, "{\"mood\":\"TIRED\"}")).isEqualTo(200);

        Map<String, Object> after = snapshots("snp04").get(0);
        assertThat(after.get("is_stale")).isEqualTo(true);
        assertThat(after.get("score")).isEqualTo(before.get("score"));
        assertThat(after.get("level")).isEqualTo(before.get("level"));
        assertThat(after.get("calculated_at")).isEqualTo(before.get("calculated_at"));

        clock.set("2026-11-12T05:00:00Z"); // 다음 날: 스냅샷이 없던 날
        assertThat(submit(s, "{\"mood\":\"TIRED\"}")).isEqualTo(201);
        assertThat(snapshots("snp04")).hasSize(1);

        engine.fail(false);
        assertThat(submit(s, "{\"mood\":\"TIRED\"}")).isEqualTo(200);
        assertThat(snapshots("snp04")).hasSize(2);
        assertThat(snapshots("snp04").get(1).get("is_stale")).isEqualTo(false);
    }

    @Test
    void 동의하지_않은_소스의_데이터는_engine에_넘기지_않는다() throws Exception {
        MockHttpSession s = ready("snp05");
        assertThat(mockMvc.perform(post("/api/v1/me/connections/STEPS").session(s).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        Long id = userId("snp05");
        // 동의한 STEPS와 동의하지 않은 SLEEP 행이 모두 DB에 있다
        jdbc.update("""
                INSERT INTO daily_signals (user_id, local_date, source, metric_key, value, unit, provider, collected_at)
                VALUES (?, ?, 'STEPS', 'STEPS_DAILY', 2000, 'count', 'DEMO', now()),
                       (?, ?, 'SLEEP', 'SLEEP_MINUTES', 300, 'min', 'DEMO', now())
                """, id, TODAY, id, TODAY);

        submit(s, "{\"mood\":\"OKAY\"}");

        FatigueRequest request = engine.lastRequest();
        assertThat(request.signals()).extracting(SignalInput::source).containsExactly("STEPS");
        assertThat(request.consentedSources()).containsExactly("STEPS");
        assertThat(snapshots("snp05").get(0).get("used_sources")).isEqualTo("[\"CHECKIN\",\"STEPS\"]");
    }

    @Test
    void engine_요청에는_가명_ID만_들어간다() throws Exception {
        MockHttpSession s = ready("snp06");

        submit(s, "{\"mood\":\"OKAY\",\"supportNeed\":\"VENT\"}");

        FatigueRequest request = engine.lastRequest();
        String subjectKey = jdbc.queryForObject("SELECT subject_key FROM users WHERE login_id = 'snp06'", String.class);
        assertThat(request.subjectKey()).isEqualTo(subjectKey);
        assertThat(request.localDate()).isEqualTo(TODAY);
        assertThat(request.timezone().getId()).isEqualTo("Asia/Seoul");
        assertThat(request.checkin().mood()).isEqualTo("OKAY");
        assertThat(request.checkin().supportNeed()).isEqualTo("VENT");
        assertThat(objectMapper.writeValueAsString(request)).doesNotContain("snp06").doesNotContain("userId");
    }

    @Test
    void 다른_사용자의_체크인은_내_계산에_쓰이지_않는다() throws Exception {
        MockHttpSession a = ready("snp07a");
        MockHttpSession b = ready("snp07b");
        submit(a, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}");

        submit(b, "{\"mood\":\"CALM\"}");

        FatigueRequest request = engine.lastRequest();
        assertThat(request.checkin().mood()).isEqualTo("CALM");
        assertThat(request.checkin().stressors()).isEmpty();
        assertThat(snapshots("snp07a").get(0).get("score")).isNotEqualTo(snapshots("snp07b").get(0).get("score"));
    }
}
