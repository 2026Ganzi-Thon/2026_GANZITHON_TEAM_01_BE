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
import com.som.support.SwitchableChatEngine;
import com.som.support.TestSupportConfig;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MvcResult;

/** A_SPEC §15 통합 시나리오 3·4 + §16 필수 테스트 일부(phase 3 범위). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class Phase3ScenarioTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableChatEngine chatEngine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        chatEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "sc3");
    }

    private MvcResult postJson(MockHttpSession s, String path, String body) throws Exception {
        return mockMvc.perform(post(path).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private JsonNode getJson(MockHttpSession s, String path) throws Exception {
        MvcResult r = mockMvc.perform(get(path).session(s)).andReturn();
        assertThat(r.getResponse().getStatus()).as(path).isEqualTo(200);
        return json(objectMapper, r);
    }

    @Test
    void 시나리오3_체크인_인사_대화_카드수락_루틴4개_마음정리_털깎임_단계불변() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "sc301");
        assertThat(postJson(s, "/api/v1/checkins", "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}")
                .getResponse().getStatus()).isEqualTo(201);
        JsonNode before = getJson(s, "/api/v1/state/today");

        // 선제 인사(체크인이 있으니 Mock이 루틴 추천 카드를 붙인다)
        JsonNode items = getJson(s, "/api/v1/chat/messages?date=2026-11-11").path("items");
        assertThat(items.get(0).path("role").asText()).isEqualTo("SOMI");
        long card = -1;
        for (JsonNode item : items) {
            if (item.path("messageType").asText().equals("ROUTINE_RECOMMENDATION")) {
                card = item.path("id").asLong();
            }
        }
        assertThat(card).isPositive();

        // 메시지 → 답장
        assertThat(postJson(s, "/api/v1/chat/messages", objectMapper.writeValueAsString(Map.of("text", "조금 지쳤어")))
                .getResponse().getStatus()).isEqualTo(201);
        // 카드 수락
        assertThat(postJson(s, "/api/v1/chat/recommendations/" + card + "/response", "{\"response\":\"ACCEPTED\"}")
                .getResponse().getStatus()).isEqualTo(200);

        // 4개 미완료 마음 정리는 403
        assertThat(postJson(s, "/api/v1/journal", "{}").getResponse().getStatus()).isEqualTo(403);

        List<String> routineIds = new ArrayList<>();
        getJson(s, "/api/v1/routines/today").path("routines").forEach(r -> routineIds.add(r.path("routineId").asText()));
        assertThat(routineIds).hasSize(4);
        for (String id : routineIds) {
            assertThat(postJson(s, "/api/v1/routines/" + id + "/complete", "").getResponse().getStatus()).isEqualTo(200);
        }
        // 루틴 중복 완료: 기록 1행
        postJson(s, "/api/v1/routines/" + routineIds.get(0) + "/complete", "");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routine_sessions r JOIN users u ON u.id = r.user_id "
                + "WHERE u.login_id = 'sc301'", Integer.class)).isEqualTo(4);
        assertThat(getJson(s, "/api/v1/routines/today").path("journalUnlocked").asBoolean()).isTrue();

        assertThat(postJson(s, "/api/v1/journal", "{\"emotion\":\"TIRED\",\"text\":\"회의가 길었다\"}")
                .getResponse().getStatus()).isEqualTo(201);

        JsonNode after = getJson(s, "/api/v1/state/today");
        assertThat(after.path("woolTrimmedToday").asBoolean()).isTrue();
        assertThat(after.path("level").asInt()).isEqualTo(before.path("level").asInt());
        assertThat(after.path("score").asInt()).isEqualTo(before.path("score").asInt());
    }

    @Test
    void 시나리오4_여러_날_대화의_날짜_목록과_특정_날짜_조회() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "sc302");
        clock.set("2026-11-02T05:00:00Z");
        postJson(s, "/api/v1/chat/messages", objectMapper.writeValueAsString(Map.of("text", "월요일 이야기")));
        clock.set("2026-11-06T05:00:00Z");
        postJson(s, "/api/v1/chat/messages", objectMapper.writeValueAsString(Map.of("text", "금요일 이야기")));
        clock.set("2026-11-11T05:00:00Z");

        JsonNode dates = getJson(s, "/api/v1/chat/dates?month=2026-11");
        assertThat(dates).hasSize(2);
        assertThat(dates.get(0).asText()).isEqualTo("2026-11-02");

        JsonNode friday = getJson(s, "/api/v1/chat/messages?date=2026-11-06").path("items");
        assertThat(friday).anyMatch(m -> m.path("content").asText().equals("금요일 이야기"));
        assertThat(friday).noneMatch(m -> m.path("content").asText().equals("월요일 이야기"));
    }

    @Test
    void OpenAPI_문서에_phase3_경로가_있다() throws Exception {
        JsonNode paths = json(objectMapper, mockMvc.perform(get("/v3/api-docs")).andReturn()).path("paths");
        for (String path : List.of("/api/v1/routines/today", "/api/v1/routines/{routineId}/complete",
                "/api/v1/journal", "/api/v1/chat/messages", "/api/v1/chat/dates",
                "/api/v1/chat/recommendations/{messageId}/response", "/api/v1/help-resources",
                "/api/v1/me/notification-settings")) {
            assertThat(paths.has(path)).as(path).isTrue();
        }
    }
}
