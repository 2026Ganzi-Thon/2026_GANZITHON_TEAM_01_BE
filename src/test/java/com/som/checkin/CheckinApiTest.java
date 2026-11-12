package com.som.checkin;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class CheckinApiTest {

    static final String CHECKINS = "/api/v1/checkins";
    static final String TODAY = "2026-11-11"; // MutableClock.DEFAULT (KST)

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @AfterEach
    void cleanUp() {
        clock.reset();
        ApiTestSupport.deleteUsers(jdbc, "cka");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private MvcResult submit(MockHttpSession session, String body) throws Exception {
        return submit(session, body, null);
    }

    private MvcResult submit(MockHttpSession session, String body, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder req = post(CHECKINS).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult query(MockHttpSession session, String query) throws Exception {
        return mockMvc.perform(get(CHECKINS + query).session(session)).andReturn();
    }

    private int rows(String loginId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM checkins c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id = ?", Integer.class, loginId);
    }

    private void assertValidation(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(objectMapper, result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void 처음_제출은_201_같은_날_재제출은_200이고_값이_갱신된다() throws Exception {
        MockHttpSession s = ready("cka01");

        MvcResult first = submit(s, "{\"mood\":\"TIRED\",\"stressors\":[\"WORK\"],\"supportNeed\":\"COMFORT\"}");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(objectMapper, first);
        assertThat(body.path("localDate").asText()).isEqualTo(TODAY);
        assertThat(body.path("mood").asText()).isEqualTo("TIRED");
        assertThat(body.path("stressors").get(0).asText()).isEqualTo("WORK");
        assertThat(body.path("status").asText()).isEqualTo("SUBMITTED");

        MvcResult second = submit(s, "{\"mood\":\"CALM\"}");
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        JsonNode updated = json(objectMapper, second);
        assertThat(updated.path("mood").asText()).isEqualTo("CALM");
        assertThat(updated.path("stressors")).isEmpty();
        assertThat(updated.path("supportNeed").isNull()).isTrue();
        assertThat(rows("cka01")).isEqualTo(1);
    }

    @Test
    void 필수_약관_미동의면_403_CONSENT_REQUIRED() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "cka02");

        MvcResult result = submit(s, "{\"mood\":\"CALM\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(objectMapper, result).path("error").path("code").asText()).isEqualTo("CONSENT_REQUIRED");
        assertThat(rows("cka02")).isZero();
    }

    @Test
    void 바디에_날짜_userId_자유입력이_오면_400() throws Exception {
        MockHttpSession s = ready("cka03");
        for (String body : new String[] {
                "{\"mood\":\"CALM\",\"localDate\":\"2026-11-01\"}",
                "{\"mood\":\"CALM\",\"userId\":1}",
                "{\"mood\":\"OTHER\",\"memo\":\"자유 입력\"}"}) {
            assertValidation(submit(s, body));
        }
        assertThat(rows("cka03")).isZero();
    }

    @Test
    void 모르는_enum_개수_초과_중복은_400() throws Exception {
        MockHttpSession s = ready("cka04");
        assertValidation(submit(s, "{\"mood\":\"HAPPY\"}"));
        assertValidation(submit(s, "{\"stressors\":[\"WORK\",\"COMPARISON\"]}"));
        assertValidation(submit(s, "{\"stressors\":[\"WORK\",\"WORK\"]}"));
        assertValidation(submit(s, "{\"supportNeed\":\"HUG\"}"));
        assertThat(rows("cka04")).isZero();
    }

    @Test
    void 세_문항을_모두_건너뛰면_201_SKIPPED() throws Exception {
        MockHttpSession s = ready("cka05");

        MvcResult result = submit(s, "{}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(objectMapper, result);
        assertThat(body.path("status").asText()).isEqualTo("SKIPPED");
        assertThat(body.path("mood").isNull()).isTrue();
    }

    @Test
    void 같은_Idempotency_Key로_재시도하면_같은_응답이고_행은_하나() throws Exception {
        MockHttpSession s = ready("cka06");
        String body = "{\"mood\":\"ANXIOUS\"}";

        MvcResult first = submit(s, body, "retry-1");
        MvcResult retry = submit(s, body, "retry-1");

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(rows("cka06")).isEqualTo(1);
        assertThat(submit(s, "{\"mood\":\"CALM\"}", "retry-1").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void 날짜는_서버가_서울_기준으로_정한다() throws Exception {
        MockHttpSession s = ready("cka07");

        clock.set("2026-11-11T14:59:59Z"); // KST 11-11 23:59:59
        assertThat(json(objectMapper, submit(s, "{\"mood\":\"CALM\"}")).path("localDate").asText())
                .isEqualTo("2026-11-11");
        clock.set("2026-11-11T15:00:00Z"); // KST 11-12 00:00
        MvcResult next = submit(s, "{\"mood\":\"TIRED\"}");
        assertThat(next.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(objectMapper, next).path("localDate").asText()).isEqualTo("2026-11-12");
        assertThat(rows("cka07")).isEqualTo(2);
    }

    @Test
    void 날짜로_조회하고_없으면_404() throws Exception {
        MockHttpSession s = ready("cka08");
        submit(s, "{\"mood\":\"OKAY\"}");

        MvcResult found = query(s, "?date=" + TODAY);
        assertThat(found.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(objectMapper, found).path("mood").asText()).isEqualTo("OKAY");
        assertThat(query(s, "?date=2026-11-10").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void 다른_사용자의_체크인은_보이지_않는다() throws Exception {
        MockHttpSession a = ready("cka09a");
        MockHttpSession b = ready("cka09b");
        submit(a, "{\"mood\":\"TIRED\"}");
        Long aId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'cka09a'", Long.class);

        assertThat(query(b, "?date=" + TODAY).getResponse().getStatus()).isEqualTo(404);
        assertThat(query(b, "?date=" + TODAY + "&userId=" + aId).getResponse().getStatus()).isEqualTo(404);
        assertThat(json(objectMapper, query(b, "?from=2026-11-01&to=" + TODAY))).isEmpty();
    }

    @Test
    void 기간_조회는_날짜_오름차순이고_잘못된_범위는_400() throws Exception {
        MockHttpSession s = ready("cka10");
        clock.set("2026-11-09T05:00:00Z");
        submit(s, "{\"mood\":\"CALM\"}");
        clock.set("2026-11-11T05:00:00Z");
        submit(s, "{\"mood\":\"TIRED\"}");

        JsonNode list = json(objectMapper, query(s, "?from=2026-11-01&to=2026-11-30"));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("localDate").asText()).isEqualTo("2026-11-09");
        assertThat(list.get(1).path("localDate").asText()).isEqualTo("2026-11-11");

        assertValidation(query(s, "?from=2026-11-11&to=2026-11-01"));
        assertValidation(query(s, "?from=2026-10-01&to=2026-11-30"));
        assertValidation(query(s, ""));
        assertValidation(query(s, "?date=" + TODAY + "&from=2026-11-01&to=2026-11-30"));
        assertValidation(query(s, "?date=bad"));
    }

    @Test
    void 미인증은_401_CSRF_없으면_403() throws Exception {
        assertThat(mockMvc.perform(get(CHECKINS + "?date=" + TODAY)).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        MockHttpSession s = ready("cka11");
        assertThat(mockMvc.perform(post(CHECKINS).session(s).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
