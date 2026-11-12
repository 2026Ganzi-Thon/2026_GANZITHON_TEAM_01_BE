package com.som.settings;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
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

/** 알림 설정 저장·조회(A_SPEC §10). 발송은 P1이라 없다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class NotificationSettingsTest {

    static final String SETTINGS = "/api/v1/me/notification-settings";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "ntf");
    }

    private JsonNode read(MockHttpSession s) throws Exception {
        MvcResult r = mockMvc.perform(get(SETTINGS).session(s)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json(objectMapper, r);
    }

    private MvcResult update(MockHttpSession s, String body) throws Exception {
        return mockMvc.perform(patch(SETTINGS).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    @Test
    void 처음에는_기본값이고_저장하지_않는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf01");

        JsonNode body = read(s);

        assertThat(body.path("enabled").asBoolean()).isFalse();
        assertThat(body.path("slot").isNull()).isTrue();
        assertThat(body.path("time").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_settings n JOIN users u ON u.id = n.user_id "
                + "WHERE u.login_id = 'ntf01'", Integer.class)).isZero();
    }

    @Test
    void 보낸_필드만_바뀐다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf02");

        assertThat(update(s, "{\"enabled\":true,\"slot\":\"BEFORE_SLEEP\",\"time\":\"22:30\"}").getResponse()
                .getStatus()).isEqualTo(200);
        update(s, "{\"time\":\"23:00\"}");

        JsonNode body = read(s);
        assertThat(body.path("enabled").asBoolean()).isTrue();
        assertThat(body.path("slot").asText()).isEqualTo("BEFORE_SLEEP");
        assertThat(body.path("time").asText()).isEqualTo("23:00");

        update(s, "{\"time\":null}");
        assertThat(read(s).path("time").isNull()).isTrue();
    }

    @Test
    void 알림_없이를_고르면_꺼진다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf03");

        JsonNode body = json(objectMapper, update(s, "{\"enabled\":true,\"slot\":\"NONE\"}"));

        assertThat(body.path("enabled").asBoolean()).isFalse();
        assertThat(body.path("slot").asText()).isEqualTo("NONE");
    }

    @Test
    void 잘못된_시각_슬롯_필드는_400() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf04");
        for (String bad : new String[] {"{\"time\":\"25:00\"}", "{\"time\":\"9:5\"}", "{\"time\":\"abc\"}",
                "{\"slot\":\"NIGHT\"}", "{\"userId\":1}", "{\"enabled\":\"yes\"}"}) {
            MvcResult r = update(s, bad);
            assertThat(r.getResponse().getStatus()).as(bad).isEqualTo(400);
        }
    }

    @Test
    void 다른_사용자의_설정은_서로_영향이_없다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf05a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf05b");

        update(a, "{\"enabled\":true,\"slot\":\"MORNING\",\"time\":\"08:00\"}");

        assertThat(read(b).path("enabled").asBoolean()).isFalse();
        assertThat(read(b).path("slot").isNull()).isTrue();
    }

    @Test
    void 미동의_403_CSRF_없음_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "ntf06");
        assertThat(mockMvc.perform(get(SETTINGS).session(s)).andReturn().getResponse().getStatus()).isEqualTo(403);
        MockHttpSession ok = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ntf07");
        assertThat(mockMvc.perform(patch(SETTINGS).session(ok).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
