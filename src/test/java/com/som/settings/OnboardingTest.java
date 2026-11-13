package com.som.settings;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.routine.service.RoutineRecommendationService;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import java.time.LocalDate;
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

/** 온보딩 5문항(A_SPEC §4): 모든 문항 건너뛰기 가능, 본인 것만, Q5는 알림 기본값, 루틴 추천 입력. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class OnboardingTest {

    static final String FULL = """
            {"lifeStage":"JOB_SEEKING","fatigueSigns":["SLEEP_CHANGE","CANT_FOCUS"],
             "recoveryStyles":["MOVE_BODY"],"availableMinutes":5,"preferredContactTime":"AFTER_WORK"}""";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoutineRecommendationService routines;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "onb");
    }

    private MvcResult putAnswers(MockHttpSession s, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/me/onboarding").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode getAnswers(MockHttpSession s) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me/onboarding").session(s)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(objectMapper, result);
    }

    private Long userId(String loginId) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    @Test
    void 답하지_않았으면_모두_null이다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb01");

        JsonNode body = getAnswers(s);

        assertThat(body.path("lifeStage").isNull()).isTrue();
        assertThat(body.path("fatigueSigns").isNull()).isTrue();
        assertThat(body.path("availableMinutes").isNull()).isTrue();
        assertThat(body.path("preferredContactTime").isNull()).isTrue();
    }

    @Test
    void 저장하면_그대로_조회되고_다시_저장하면_바뀐다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb02");

        assertThat(putAnswers(s, FULL).getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = getAnswers(s);
        assertThat(saved.path("lifeStage").asText()).isEqualTo("JOB_SEEKING");
        assertThat(saved.path("fatigueSigns")).hasSize(2);
        assertThat(saved.path("availableMinutes").asInt()).isEqualTo(5);

        assertThat(putAnswers(s, "{\"lifeStage\":\"OTHER\"}").getResponse().getStatus()).isEqualTo(200);
        JsonNode changed = getAnswers(s);
        assertThat(changed.path("lifeStage").asText()).isEqualTo("OTHER");
        assertThat(changed.path("availableMinutes").isNull()).isTrue();
    }

    @Test
    void 선택지_밖의_값은_400이고_저장되지_않는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb03");

        assertThat(putAnswers(s, "{\"availableMinutes\":7}").getResponse().getStatus()).isEqualTo(400);
        assertThat(putAnswers(s, "{\"lifeStage\":\"CEO\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(putAnswers(s, "{\"fatigueSigns\":[null]}").getResponse().getStatus()).isEqualTo(400);
        assertThat(putAnswers(s, "{\"userId\":1}").getResponse().getStatus()).isEqualTo(400);
        assertThat(getAnswers(s).path("lifeStage").isNull()).isTrue();
    }

    @Test
    void 다른_사용자의_답은_보이지도_바뀌지도_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb04");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb05");
        putAnswers(a, FULL);

        assertThat(getAnswers(b).path("lifeStage").isNull()).isTrue();
        putAnswers(b, "{\"lifeStage\":\"OTHER\"}");
        assertThat(getAnswers(a).path("lifeStage").asText()).isEqualTo("JOB_SEEKING");
    }

    @Test
    void 미인증은_401_CSRF_없으면_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb06");

        assertThat(mockMvc.perform(get("/api/v1/me/onboarding")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(put("/api/v1/me/onboarding").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(FULL)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void 말을_걸면_좋은_때가_알림_시간대_기본값이_된다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb07");
        putAnswers(s, FULL);

        JsonNode settings = json(objectMapper,
                mockMvc.perform(get("/api/v1/me/notification-settings").session(s)).andReturn());

        assertThat(settings.path("slot").asText()).isEqualTo("AFTER_WORK");
    }

    @Test
    void 회복_방식과_시간이_루틴_추천_입력에_들어간다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb08");
        putAnswers(s, FULL);

        var context = routines.context(userId("onb08"), LocalDate.parse("2026-11-11"));

        assertThat(context.preferredRoutineIds())
                .containsExactlyInAnyOrder("SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");
        assertThat(context.availableSeconds()).isEqualTo(300);
    }

    @Test
    void 기록_삭제로는_남고_탈퇴하면_지워진다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "onb09");
        putAnswers(s, FULL);
        Long id = userId("onb09");

        mockMvc.perform(delete("/api/v1/me/data").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andReturn();
        assertThat(getAnswers(s).path("lifeStage").asText()).isEqualTo("JOB_SEEKING");

        mockMvc.perform(delete("/api/v1/me").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andReturn();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_answers WHERE user_id = ?",
                Integer.class, id)).isZero();
    }
}
