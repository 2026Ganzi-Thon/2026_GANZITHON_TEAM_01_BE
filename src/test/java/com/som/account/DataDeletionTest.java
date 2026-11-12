package com.som.account;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.SwitchableEngineDataEraser;
import com.som.support.TestSupportConfig;
import com.som.support.UserDataSeeder;
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

/** 기록 삭제와 처리 상태(A_SPEC §10·§11, §16 전체 삭제 후 재조회 불가). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class DataDeletionTest {

    static final String CONFIRM = "{\"confirm\":true}";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SwitchableEngineDataEraser engineEraser;

    @AfterEach
    void cleanUp() {
        engineEraser.reset();
        ApiTestSupport.deleteUsers(jdbc, "ddl");
        jdbc.update("DELETE FROM deletion_requests");
    }

    private MvcResult deleteData(MockHttpSession s, String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/me/data").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode getJson(MockHttpSession s, String path) throws Exception {
        return json(objectMapper, mockMvc.perform(get(path).session(s)).andReturn());
    }

    @Test
    void 기록을_지우면_COMPLETED이고_재조회가_비며_로그인은_계속된다() throws Exception {
        MockHttpSession s = UserDataSeeder.seedAll(mockMvc, objectMapper, "ddl01");
        String subjectKey = jdbc.queryForObject("SELECT subject_key FROM users WHERE login_id = 'ddl01'", String.class);

        MvcResult result = deleteData(s, CONFIRM);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(objectMapper, result);
        assertThat(body.path("scope").asText()).isEqualTo("DATA");
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("completedAt").asText()).endsWith("+09:00");
        assertThat(engineEraser.erased()).containsExactly(subjectKey);

        assertThat(mockMvc.perform(get("/api/v1/checkins?date=2026-11-11").session(s)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        assertThat(getJson(s, "/api/v1/insights/week").path("recordedDays").asInt()).isZero();
        assertThat(getJson(s, "/api/v1/journal?date=2026-11-11")).isEmpty();
        assertThat(getJson(s, "/api/v1/chat/dates?month=2026-11")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id = 'ddl01'", Integer.class)).isZero();
        assertThat(mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", "ddl01", "password",
                        ApiTestSupport.PASSWORD)))).andReturn().getResponse().getStatus()).isEqualTo(200);

        JsonNode status = getJson(s, "/api/v1/me/deletion-status");
        assertThat(status.path("scope").asText()).isEqualTo("DATA");
        assertThat(status.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void 확인_없이는_400_CSRF_없으면_403_미인증_401() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ddl02");

        assertThat(deleteData(s, "{}").getResponse().getStatus()).isEqualTo(400);
        assertThat(deleteData(s, "{\"confirm\":false}").getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(delete("/api/v1/me/data").session(s).contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRM)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(delete("/api/v1/me/data").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRM)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/v1/me/deletion-status").session(s)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }

    @Test
    void engine_삭제가_실패하면_ENGINE_PENDING이고_DB는_지워져_있다() throws Exception {
        MockHttpSession s = UserDataSeeder.seedAll(mockMvc, objectMapper, "ddl03");
        engineEraser.fail(true);

        JsonNode body = json(objectMapper, deleteData(s, CONFIRM));

        assertThat(body.path("status").asText()).isEqualTo("ENGINE_PENDING");
        assertThat(body.path("completedAt").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkins c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id = 'ddl03'", Integer.class)).isZero();
    }

    @Test
    void 필수_약관_미동의_사용자도_지울_수_있다() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "ddl04");

        assertThat(deleteData(s, CONFIRM).getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/v1/me/deletion-status").session(s)).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
    }

    @Test
    void 다른_사용자의_데이터와_상태는_영향이_없다() throws Exception {
        MockHttpSession a = UserDataSeeder.seedAll(mockMvc, objectMapper, "ddl05a");
        MockHttpSession b = UserDataSeeder.seedAll(mockMvc, objectMapper, "ddl05b");

        deleteData(a, CONFIRM);

        Long bId = UserDataSeeder.userId(jdbc, "ddl05b");
        for (String table : UserDataSeeder.USER_TABLES) {
            assertThat(UserDataSeeder.rows(jdbc, table, bId)).as(table).isPositive();
        }
        assertThat(mockMvc.perform(get("/api/v1/me/deletion-status").session(b)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'deletion_requests'", String.class)).doesNotContain("user_id");
    }
}
