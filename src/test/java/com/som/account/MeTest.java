package com.som.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00
    static final String ME = "/api/v1/me";
    static final String PASSWORD = "abcde12345";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_consents WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'me%')");
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'me%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'me%'");
    }

    private MockHttpSession signup(String loginId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String json = objectMapper.writeValueAsString(
                Map.of("loginId", loginId, "password", PASSWORD, "passwordConfirm", PASSWORD));
        int status = mockMvc.perform(post("/api/v1/auth/signup").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(201);
        return session;
    }

    private JsonNode getMe(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(ME).session(session)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void 로그인하면_200_loginId와_미완료_동의() throws Exception {
        MockHttpSession session = signup("mebasic1");

        JsonNode body = getMe(session);

        assertThat(body.path("loginId").asText()).isEqualTo("mebasic1");
        assertThat(body.path("requiredConsentsCompleted").asBoolean()).isFalse();
        assertThat(OffsetDateTime.parse(body.path("createdAt").asText()).toInstant()).isEqualTo(FIXED);
    }

    @Test
    void 필수_3개_동의_후에는_true() throws Exception {
        MockHttpSession session = signup("megrant1");
        String json = objectMapper.writeValueAsString(Map.of("consents", List.of(
                Map.of("key", "AGE_14_PLUS", "granted", true),
                Map.of("key", "TERMS_OF_SERVICE", "granted", true),
                Map.of("key", "PRIVACY_COLLECTION", "granted", true))));
        int status = mockMvc.perform(patch("/api/v1/me/consents").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);

        assertThat(getMe(session).path("requiredConsentsCompleted").asBoolean()).isTrue();
    }

    @Test
    void 응답에_내부_식별자와_비밀값이_없다() throws Exception {
        JsonNode body = getMe(signup("mehide01"));

        assertThat(body.has("id")).isFalse();
        assertThat(body.has("userId")).isFalse();
        assertThat(body.has("subjectKey")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(body.has("authProvider")).isFalse();
        assertThat(body.size()).isEqualTo(3);
    }

    @Test
    void 다른_사용자_id를_쿼리로_붙여도_세션_사용자만_나온다() throws Exception {
        MockHttpSession sessionA = signup("meusera1");
        MockHttpSession sessionB = signup("meuserb1");
        Long idB = users.findByLoginId("meuserb1").orElseThrow().getId();

        assertThat(getMe(sessionA).path("loginId").asText()).isEqualTo("meusera1");
        assertThat(getMe(sessionB).path("loginId").asText()).isEqualTo("meuserb1");

        MvcResult result = mockMvc.perform(get(ME).param("userId", String.valueOf(idB)).session(sessionA)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("loginId").asText())
                .isEqualTo("meusera1");
    }

    @Test
    void 미인증이면_401() throws Exception {
        MvcResult result = mockMvc.perform(get(ME)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("error").path("code").asText())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void 사용자_행이_지워진_세션은_401이고_세션이_무효화된다() throws Exception {
        MockHttpSession session = signup("megone01");
        jdbc.update("DELETE FROM users WHERE login_id = 'megone01'");

        MvcResult result = mockMvc.perform(get(ME).session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("error").path("code").asText())
                .isEqualTo("UNAUTHORIZED");
        assertThat(session.isInvalid()).isTrue();
    }
}
