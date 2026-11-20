package com.som.routine;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableRoutineEngine;
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
import org.springframework.test.web.servlet.MvcResult;

/** 루틴 실행 기록·오늘 화면(A_SPEC §9, §16 루틴 중복 완료). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RoutineSessionTest {

    static final List<String> CATALOG = List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableRoutineEngine routineEngine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        routineEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "rts");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private MvcResult action(MockHttpSession s, String routineId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/routines/" + routineId + "/" + action).session(s).with(csrf()))
                .andReturn();
    }

    private JsonNode today(MockHttpSession s) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/routines/today").session(s)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(objectMapper, result);
    }

    private int rows(String loginId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM routine_sessions r JOIN users u ON u.id = r.user_id "
                + "WHERE u.login_id = ?", Integer.class, loginId);
    }

    @Test
    void 같은_루틴을_두_번_완료해도_행은_하나이고_응답은_같다() throws Exception {
        MockHttpSession s = ready("rts01");

        MvcResult first = action(s, "BREATHING", "complete");
        clock.set("2026-11-11T05:10:00Z");
        MvcResult second = action(s, "BREATHING", "complete");

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        JsonNode body = json(objectMapper, first);
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("localDate").asText()).isEqualTo("2026-11-11");
        assertThat(body.path("finishedAt").asText()).endsWith("+09:00");
        assertThat(rows("rts01")).isEqualTo(1);
    }

    @Test
    void 상태_전이_규칙() throws Exception {
        MockHttpSession s = ready("rts02");

        assertThat(json(objectMapper, action(s, "SHOULDER_ROLL", "start")).path("status").asText()).isEqualTo("STARTED");
        assertThat(json(objectMapper, action(s, "SHOULDER_ROLL", "skip")).path("status").asText()).isEqualTo("SKIPPED");
        assertThat(json(objectMapper, action(s, "SHOULDER_ROLL", "start")).path("status").asText()).isEqualTo("STARTED");
        assertThat(json(objectMapper, action(s, "SHOULDER_ROLL", "complete")).path("status").asText())
                .isEqualTo("COMPLETED");

        MvcResult skipAfterDone = action(s, "SHOULDER_ROLL", "skip");
        assertThat(skipAfterDone.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(objectMapper, skipAfterDone).path("error").path("code").asText()).isEqualTo("CONFLICT");
        assertThat(action(s, "SHOULDER_ROLL", "start").getResponse().getStatus()).isEqualTo(409);
        assertThat(rows("rts02")).isEqualTo(1);
    }

    @Test
    void 모르는_루틴은_404() throws Exception {
        MockHttpSession s = ready("rts03");
        assertThat(action(s, "YOGA", "complete").getResponse().getStatus()).isEqualTo(404);
        assertThat(action(s, "BREATHING", "pause").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void 오늘_화면은_추천_순서와_상태를_보여주고_4개_완료_후_마음_정리가_열린다() throws Exception {
        MockHttpSession s = ready("rts04");

        JsonNode before = today(s);
        assertThat(before.path("localDate").asText()).isEqualTo("2026-11-11");
        assertThat(before.path("routines")).hasSize(4);
        assertThat(before.path("routines").get(0).path("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(before.path("routines").get(0).path("durationSec").asInt()).isPositive();
        assertThat(before.path("journalUnlocked").asBoolean()).isFalse();
        assertThat(before.path("isStale").asBoolean()).isFalse();

        for (int i = 0; i < 3; i++) {
            action(s, CATALOG.get(i), "complete");
        }
        assertThat(today(s).path("journalUnlocked").asBoolean()).isFalse();
        action(s, CATALOG.get(3), "complete");

        JsonNode after = today(s);
        assertThat(after.path("journalUnlocked").asBoolean()).isTrue();
        after.path("routines").forEach(r -> assertThat(r.path("status").asText()).isEqualTo("COMPLETED"));
    }

    @Test
    void 주간_이행은_1개_이상_완료한_날이고_미래는_null() throws Exception {
        MockHttpSession s = ready("rts05");
        clock.set("2026-11-09T05:00:00Z"); // 월
        action(s, "BREATHING", "complete");
        clock.set("2026-11-10T05:00:00Z"); // 화: 건너뜀만
        action(s, "BREATHING", "skip");
        clock.set("2026-11-11T05:00:00Z"); // 수
        action(s, "SEATED_TWIST", "complete");

        JsonNode week = today(s).path("week");

        assertThat(week).hasSize(7);
        assertThat(week.get(0).path("localDate").asText()).isEqualTo("2026-11-09");
        assertThat(week.get(0).path("done").asBoolean()).isTrue();
        assertThat(week.get(1).path("done").asBoolean()).isFalse();
        assertThat(week.get(2).path("done").asBoolean()).isTrue();
        for (int i = 3; i < 7; i++) {
            assertThat(week.get(i).path("done").isNull()).as("future " + i).isTrue();
        }
    }

    @Test
    void 오늘_루틴_전체_완료시에만_한단계_감소하고_중복과_재계산에_유지한다() throws Exception {
        MockHttpSession s = ready("rts06");
        MockHttpSession other = ready("rtslevelother");
        String body = "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"]}";
        for (var session : List.of(s, other)) {
            mockMvc.perform(post("/api/v1/checkins").session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        }
        int before = today(s).path("level").asInt();
        assertThat(before).isGreaterThan(1);
        for (String id : CATALOG.subList(0, CATALOG.size() - 1)) {
            action(s, id, "complete");
            assertThat(today(s).path("level").asInt()).isEqualTo(before);
        }
        action(s, CATALOG.get(0), "complete");
        assertThat(today(s).path("level").asInt()).isEqualTo(before);
        action(s, CATALOG.get(CATALOG.size() - 1), "complete");
        int reduced = Math.max(1, before - 1);
        assertThat(today(s).path("level").asInt()).isEqualTo(reduced);
        for (String id : CATALOG) action(s, id, "complete");
        assertThat(today(s).path("level").asInt()).isEqualTo(reduced);
        assertThat(today(other).path("level").asInt()).isEqualTo(before);
        mockMvc.perform(post("/api/v1/checkins").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(today(s).path("level").asInt()).isEqualTo(reduced);
        var home = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(s)).andReturn());
        assertThat(home.path("level").asInt()).isEqualTo(reduced);
        assertThat(home.path("assetKey").asText()).isEqualTo("sheep_level_" + reduced);
        clock.set("2026-11-12T05:00:00Z");
        mockMvc.perform(post("/api/v1/checkins").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(today(s).path("level").asInt()).isEqualTo(before);
    }

    @Test
    void 완료_기록은_다음_날_추천_입력에_들어간다() throws Exception {
        MockHttpSession s = ready("rts07");
        action(s, "BREATHING", "complete");

        clock.set("2026-11-12T05:00:00Z");
        today(s);

        assertThat(routineEngine.requests().get(routineEngine.requests().size() - 1).completedRecently())
                .containsExactly("BREATHING");
    }

    @Test
    void 다른_사용자의_완료는_보이지_않는다() throws Exception {
        MockHttpSession a = ready("rts08a");
        MockHttpSession b = ready("rts08b");
        for (String id : CATALOG) {
            action(a, id, "complete");
        }

        JsonNode bToday = today(b);
        assertThat(bToday.path("journalUnlocked").asBoolean()).isFalse();
        bToday.path("routines").forEach(r -> assertThat(r.path("status").asText()).isEqualTo("NOT_STARTED"));
        assertThat(bToday.path("week").get(2).path("done").asBoolean()).isFalse();
    }

    @Test
    void 미동의는_403_CSRF_없으면_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "rts09");
        assertThat(action(s, "BREATHING", "complete").getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/v1/routines/today").session(s)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        MockHttpSession ok = ready("rts10");
        assertThat(mockMvc.perform(post("/api/v1/routines/BREATHING/complete").session(ok)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
