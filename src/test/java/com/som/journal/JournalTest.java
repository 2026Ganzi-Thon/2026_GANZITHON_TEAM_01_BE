package com.som.journal;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 마음 정리 기록·털 깎임 연출(A_SPEC §9, §16 4개 미완료 마음 정리). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class JournalTest {

    static final List<String> CATALOG = List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");
    static final String SECRET_TEXT = "팀장님과의 회의가 너무 힘들었다 secret-journal-7731";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableFatigueEngine fatigueEngine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        fatigueEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "jnl");
    }

    private MockHttpSession unlocked(String loginId) throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
        for (String id : CATALOG) {
            mockMvc.perform(post("/api/v1/routines/" + id + "/complete").session(s).with(csrf())).andReturn();
        }
        return s;
    }

    private MvcResult write(MockHttpSession s, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/journal").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String body(String emotion, String text) throws Exception {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("emotion", emotion);
        map.put("text", text);
        return objectMapper.writeValueAsString(map);
    }

    @Test
    void 루틴_4개를_마치기_전에는_403_JOURNAL_LOCKED() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "jnl01");
        mockMvc.perform(post("/api/v1/routines/BREATHING/complete").session(s).with(csrf())).andReturn();

        MvcResult result = write(s, body("TIRED", "오늘은 힘들었다"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(objectMapper, result).path("error").path("code").asText()).isEqualTo("JOURNAL_LOCKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries j JOIN users u ON u.id = j.user_id "
                + "WHERE u.login_id = 'jnl01'", Integer.class)).isZero();
    }

    @Test
    void 저장하면_201이고_오늘_상태에_털_깎임이_보이지만_점수와_단계는_그대로() throws Exception {
        MockHttpSession s = unlocked("jnl02");
        mockMvc.perform(post("/api/v1/checkins").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"TIRED\"}")).andReturn();
        JsonNode before = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(s)).andReturn());
        assertThat(before.path("woolTrimmedToday").asBoolean()).isFalse();

        MvcResult result = write(s, body("TIRED", "회의가 길었다"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode saved = json(objectMapper, result);
        assertThat(saved.path("localDate").asText()).isEqualTo("2026-11-11");
        assertThat(saved.path("emotion").asText()).isEqualTo("TIRED");
        assertThat(saved.path("text").asText()).isEqualTo("회의가 길었다");
        assertThat(saved.path("woolTrimmedToday").asBoolean()).isTrue();
        JsonNode after = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(s)).andReturn());
        assertThat(after.path("woolTrimmedToday").asBoolean()).isTrue();
        assertThat(after.path("level").asInt()).isEqualTo(before.path("level").asInt());
        assertThat(after.path("score").asInt()).isEqualTo(before.path("score").asInt());
    }

    @Test
    void 감정과_기록이_모두_비어도_저장되고_두_번_저장해도_연출은_하루_한_번() throws Exception {
        MockHttpSession s = unlocked("jnl03");

        assertThat(write(s, "{}").getResponse().getStatus()).isEqualTo(201);
        assertThat(write(s, body(null, "   ")).getResponse().getStatus()).isEqualTo(201);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wool_trim_events w JOIN users u ON u.id = w.user_id "
                + "WHERE u.login_id = 'jnl03'", Integer.class)).isEqualTo(1);
        JsonNode list = json(objectMapper, mockMvc.perform(get("/api/v1/journal?date=2026-11-11").session(s))
                .andReturn());
        assertThat(list).hasSize(2);
        assertThat(list.get(1).path("text").isNull()).isTrue(); // 공백만 → null
    }

    @Test
    void 모르는_감정_길이_초과_제어문자는_400() throws Exception {
        MockHttpSession s = unlocked("jnl04");

        assertThat(write(s, body("HAPPY", null)).getResponse().getStatus()).isEqualTo(400);
        assertThat(write(s, body(null, "가".repeat(1001))).getResponse().getStatus()).isEqualTo(400);
        assertThat(write(s, body(null, "bad\u0007bell")).getResponse().getStatus()).isEqualTo(400);
        assertThat(write(s, body(null, "가".repeat(1000))).getResponse().getStatus()).isEqualTo(201);
        assertThat(write(s, body(null, "줄\n바꿈\t탭")).getResponse().getStatus()).isEqualTo(201);
        assertThat(write(s, "{\"emotion\":\"OKAY\",\"userId\":1}").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void 스크립트_문자열은_그대로_저장되고_JSON으로만_응답한다() throws Exception {
        MockHttpSession s = unlocked("jnl05");

        MvcResult result = write(s, body(null, "<script>alert(1)</script>"));

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(json(objectMapper, result).path("text").asText()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void 기록_원문은_로그와_engine에_가지_않는다(CapturedOutput output) throws Exception {
        MockHttpSession s = unlocked("jnl06");
        mockMvc.perform(post("/api/v1/checkins").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mood\":\"OKAY\"}")).andReturn();

        write(s, body("ANXIOUS", SECRET_TEXT));
        write(s, body("NOPE", SECRET_TEXT)); // 400 경로도 로그에 원문이 없어야 한다

        assertThat(output.getAll()).doesNotContain("secret-journal-7731");
        assertThat(objectMapper.writeValueAsString(fatigueEngine.requests())).doesNotContain("secret-journal-7731")
                .doesNotContain("ANXIOUS");
    }

    @Test
    void 다른_사용자의_기록은_보이지_않는다() throws Exception {
        MockHttpSession a = unlocked("jnl07a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "jnl07b");
        write(a, body("TIRED", "A의 기록"));

        JsonNode list = json(objectMapper, mockMvc.perform(get("/api/v1/journal?date=2026-11-11").session(b))
                .andReturn());
        assertThat(list).isEmpty();
        JsonNode bState = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(b)).andReturn());
        assertThat(bState.path("woolTrimmedToday").asBoolean()).isFalse();
    }

    @Test
    void 조회에는_날짜가_필요하다() throws Exception {
        MockHttpSession s = unlocked("jnl08");
        assertThat(mockMvc.perform(get("/api/v1/journal").session(s)).andReturn().getResponse().getStatus())
                .isEqualTo(400);
    }
}
