package com.som.account;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import com.som.support.UserDataSeeder;
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

/** 탈퇴(A_SPEC §10·§11 탈퇴 시 즉시 삭제). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class WithdrawalTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "wdr");
        jdbc.update("DELETE FROM deletion_requests");
    }

    private MvcResult withdraw(MockHttpSession s, String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/me").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private int login(String loginId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", loginId, "password",
                        ApiTestSupport.PASSWORD)))).andReturn().getResponse().getStatus();
    }

    @Test
    void 탈퇴하면_모든_데이터가_지워지고_세션_로그인이_막히며_재가입은_된다() throws Exception {
        MockHttpSession s = UserDataSeeder.seedAll(mockMvc, objectMapper, "wdr01");
        Long id = UserDataSeeder.userId(jdbc, "wdr01");

        MvcResult result = withdraw(s, "{\"confirm\":true}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(objectMapper, result);
        assertThat(body.path("scope").asText()).isEqualTo("ACCOUNT");
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        for (String table : UserDataSeeder.USER_TABLES) {
            assertThat(UserDataSeeder.rows(jdbc, table, id)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = 'wdr01'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deletion_requests WHERE scope = 'ACCOUNT'",
                Integer.class)).isEqualTo(1);

        assertThat(mockMvc.perform(get("/api/v1/me").session(s)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(login("wdr01")).isEqualTo(401);

        MockHttpSession again = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "wdr01");
        assertThat(json(objectMapper, mockMvc.perform(get("/api/v1/insights/week").session(again)).andReturn())
                .path("recordedDays").asInt()).isZero();
    }

    @Test
    void 탈퇴한_사용자의_다른_세션은_어느_API든_401() throws Exception {
        MockHttpSession first = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "wdr02");
        MockHttpSession second = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/login").session(second).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", "wdr02", "password",
                        ApiTestSupport.PASSWORD)))).andReturn();

        withdraw(first, "{\"confirm\":true}");

        for (MvcResult r : List.of(
                mockMvc.perform(get("/api/v1/state/today").session(second)).andReturn(),
                mockMvc.perform(patch("/api/v1/me/consents").session(second).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consents\":[{\"key\":\"AGE_14_PLUS\",\"granted\":true}]}")).andReturn(),
                mockMvc.perform(get("/api/v1/help-resources").session(second)).andReturn())) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void 확인_없이는_400_CSRF_없으면_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "wdr03");

        assertThat(withdraw(s, "{}").getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(delete("/api/v1/me").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":true}")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = 'wdr03'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 다른_사용자는_그대로다() throws Exception {
        MockHttpSession a = UserDataSeeder.seedAll(mockMvc, objectMapper, "wdr04a");
        UserDataSeeder.seedAll(mockMvc, objectMapper, "wdr04b");

        withdraw(a, "{\"confirm\":true}");

        assertThat(login("wdr04b")).isEqualTo(200);
        Long b = UserDataSeeder.userId(jdbc, "wdr04b");
        for (String table : UserDataSeeder.USER_TABLES) {
            assertThat(UserDataSeeder.rows(jdbc, table, b)).as(table).isPositive();
        }
    }
}
