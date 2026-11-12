package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoginSessionTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00
    static final String LOGIN = "/api/v1/auth/login";
    static final String LOGOUT = "/api/v1/auth/logout";
    static final String ME = "/test/auth/me";
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
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired Clock clock;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        users.deleteAllById(created);
    }

    private User saveUser(String loginId) {
        User user = users.saveAndFlush(User.register(loginId, passwordEncoder.encode(PASSWORD),
                UUID.randomUUID().toString(), OffsetDateTime.now(clock)));
        created.add(user.getId());
        return user;
    }

    private String body(String loginId, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("loginId", loginId, "password", password));
    }

    private MvcResult login(MockHttpSession session, String loginId, String password) throws Exception {
        return mockMvc.perform(post(LOGIN).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(loginId, password)))
                .andReturn();
    }

    /** 로그인에 성공한 세션을 돌려준다. */
    private MockHttpSession loggedIn(String loginId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        assertThat(login(session, loginId, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        return session;
    }

    private void expectUnauthorized(MockHttpSession session) throws Exception {
        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void 올바른_자격이면_200_loginId와_세션이_생기고_보호경로에서_그_사용자id가_나온다() throws Exception {
        User user = saveUser("loginok1");
        MockHttpSession session = new MockHttpSession();

        MvcResult result = login(session, "loginok1", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("loginId").asText()).isEqualTo("loginok1");
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();

        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()));
    }

    @Test
    void 세션_principal에는_userId만_있다() throws Exception {
        User user = saveUser("principal1");
        MockHttpSession session = loggedIn("principal1");

        var context = (org.springframework.security.core.context.SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var auth = context.getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(new SessionUser(user.getId()));
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.toString()).doesNotContain("principal1").doesNotContain("argon2");
    }

    @Test
    void 로그인하면_세션ID가_바뀐다() throws Exception {
        saveUser("fixation1");
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        assertThat(login(session, "fixation1", PASSWORD).getResponse().getStatus()).isEqualTo(200);

        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void 없는_아이디와_틀린_비밀번호는_같은_401_INVALID_CREDENTIALS(CapturedOutput output) throws Exception {
        saveUser("wrongpw1");
        MvcResult unknown = login(new MockHttpSession(), "nouser99", PASSWORD);
        MvcResult wrong = login(new MockHttpSession(), "wrongpw1", "wrongpass99");

        for (MvcResult r : List.of(unknown, wrong)) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            JsonNode error = objectMapper.readTree(r.getResponse().getContentAsString()).path("error");
            assertThat(error.path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(error.path("message").asText()).isEqualTo("아이디 또는 비밀번호가 올바르지 않아요.");
        }
        // requestId만 요청마다 다르다. 나머지(상태·코드·메시지·키)는 완전히 같다.
        JsonNode a = objectMapper.readTree(unknown.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(wrong.getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) a.path("error")).remove("requestId");
        ((com.fasterxml.jackson.databind.node.ObjectNode) b.path("error")).remove("requestId");
        assertThat(a).isEqualTo(b);
        assertThat(unknown.getResponse().getContentAsString()).doesNotContain("nouser99");
        // 실패 로그에 loginId·비밀번호를 남기지 않는다
        assertThat(output.getAll()).doesNotContain("nouser99").doesNotContain("wrongpw1")
                .doesNotContain("wrongpass99");
    }

    @Test
    void 빈_값이면_400_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(post(LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void CSRF_없이_로그인하면_403_CSRF_INVALID() throws Exception {
        saveUser("nocsrf1");
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("nocsrf1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.CSRF_INVALID.name()));
    }

    @Test
    void 로그아웃하면_204이고_같은_세션으로_보호경로는_401() throws Exception {
        saveUser("logout1");
        MockHttpSession session = loggedIn("logout1");

        mockMvc.perform(post(LOGOUT).session(session).with(csrf())).andExpect(status().isNoContent());

        expectUnauthorized(session);
    }

    @Test
    void 세션이_없어도_로그아웃은_204() throws Exception {
        mockMvc.perform(post(LOGOUT).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void CSRF_없이_로그아웃하면_403_CSRF_INVALID() throws Exception {
        mockMvc.perform(post(LOGOUT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.CSRF_INVALID.name()));
    }

    @Test
    void 세션이_만료되면_401_UNAUTHORIZED() throws Exception {
        saveUser("expire1");
        MockHttpSession session = loggedIn("expire1");

        session.invalidate(); // 만료 시뮬레이션

        expectUnauthorized(session);
    }

    @Test
    void 다른_사용자_id를_쿼리에_넣어도_세션_사용자_id만_나오고_바디에_넣으면_400() throws Exception {
        User a = saveUser("usera01");
        User b = saveUser("userb01");
        MockHttpSession sessionA = loggedIn("usera01");

        mockMvc.perform(get(ME).session(sessionA).param("userId", String.valueOf(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(a.getId()));

        mockMvc.perform(post(ME).session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"x\",\"userId\":" + b.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        MockHttpSession sessionB = loggedIn("userb01");
        mockMvc.perform(get(ME).session(sessionB).param("userId", String.valueOf(a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(b.getId()));
    }

    @Test
    void 로그인하면_last_login_at이_고정_Clock_값으로_기록된다() throws Exception {
        saveUser("lastlog1");
        loggedIn("lastlog1");

        assertThat(users.findByLoginId("lastlog1").orElseThrow().getLastLoginAt().toInstant()).isEqualTo(FIXED);
    }

    @Test
    void 로그인_실패시에는_last_login_at이_기록되지_않는다() throws Exception {
        saveUser("lastlog2");
        login(new MockHttpSession(), "lastlog2", "wrongpass99");

        assertThat(users.findByLoginId("lastlog2").orElseThrow().getLastLoginAt()).isNull();
    }

    @Test
    void 비로그인으로_CurrentUserId_경로를_부르면_401() throws Exception {
        expectUnauthorized(new MockHttpSession());
    }
}
