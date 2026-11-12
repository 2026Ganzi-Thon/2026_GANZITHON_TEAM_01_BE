package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 로그인 요청 제한. 리필 주기를 길게 둬서 테스트 중에는 토큰이 다시 차지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "som.rate-limit.login.per-ip.capacity=6",
        "som.rate-limit.login.per-ip.refill-period=1h",
        "som.rate-limit.login.per-login-id.capacity=3",
        "som.rate-limit.login.per-login-id.refill-period=1h"})
class LoginRateLimitTest {

    static final String LOGIN = "/api/v1/auth/login";
    static final String PASSWORD = "abcde12345";
    static final int PER_LOGIN_ID = 3;
    static final int PER_IP = 6;

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

    private void saveUser(String loginId) {
        User user = users.saveAndFlush(User.register(loginId, passwordEncoder.encode(PASSWORD),
                UUID.randomUUID().toString(), OffsetDateTime.now(clock)));
        created.add(user.getId());
    }

    private MockHttpServletResponse login(String ip, String loginId, String password) throws Exception {
        return mockMvc.perform(post(LOGIN).with(csrf())
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", loginId, "password", password))))
                .andReturn().getResponse();
    }

    private void assertRateLimited(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(response.getHeader("Retry-After"))).isPositive();
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("error").path("code").asText()).isEqualTo(ErrorCode.RATE_LIMITED.name());
        assertThat(json.path("error").path("message").asText()).isEqualTo(ErrorCode.RATE_LIMITED.defaultMessage());
        assertThat(json.path("error").has("requestId")).isTrue();
    }

    @Test
    void 같은_loginId로_capacity_넘게_시도하면_마지막은_429_RATE_LIMITED와_Retry_After() throws Exception {
        saveUser("rlsame01");
        for (int i = 0; i < PER_LOGIN_ID; i++) {
            assertThat(login("10.0.1.1", "rlsame01", "wrongpass123").getStatus()).isEqualTo(401);
        }

        assertRateLimited(login("10.0.1.1", "rlsame01", "wrongpass123"));
    }

    @Test
    void 없는_아이디와_있는_아이디가_같은_방식으로_제한된다() throws Exception {
        saveUser("rlexist01");
        List<Integer> existing = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i <= PER_LOGIN_ID; i++) {
            existing.add(login("10.0.2.1", "rlexist01", "wrongpass123").getStatus());
            missing.add(login("10.0.2.2", "rlnobody01", "wrongpass123").getStatus());
        }

        assertThat(existing).containsExactly(401, 401, 401, 429);
        assertThat(missing).isEqualTo(existing);
        assertRateLimited(login("10.0.2.2", "rlnobody01", "wrongpass123"));
    }

    @Test
    void 제한된_loginId가_있어도_다른_loginId는_영향받지_않는다() throws Exception {
        saveUser("rlother01");
        for (int i = 0; i <= PER_LOGIN_ID; i++) {
            login("10.0.3.1", "rlblocked01", "wrongpass123");
        }

        assertThat(login("10.0.3.1", "rlother01", PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    void IP_버킷이_바닥나면_429이고_다른_IP는_영향받지_않는다() throws Exception {
        saveUser("rlipok01");
        for (int i = 0; i < PER_IP; i++) {
            assertThat(login("10.0.4.1", "rlip" + i + "x", "wrongpass123").getStatus()).isEqualTo(401);
        }

        assertRateLimited(login("10.0.4.1", "rlipnew01", "wrongpass123"));
        assertThat(login("10.0.4.2", "rlipok01", PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    void X_Forwarded_For를_바꿔도_IP_제한을_피할_수_없다() throws Exception {
        for (int i = 0; i < PER_IP; i++) {
            login("10.0.6.1", "rlxff" + i + "x", "wrongpass123");
        }

        MockHttpServletResponse response = mockMvc.perform(post(LOGIN).with(csrf())
                        .with(request -> { request.setRemoteAddr("10.0.6.1"); return request; })
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", "rlxffnew", "password", "x"))))
                .andReturn().getResponse();

        assertRateLimited(response);
    }

    @Test
    void 제한_상태에서는_올바른_비밀번호여도_429이고_로그인되지_않는다() throws Exception {
        saveUser("rlright01");
        for (int i = 0; i < PER_LOGIN_ID; i++) {
            login("10.0.5.1", "rlright01", "wrongpass123");
        }

        MockHttpServletResponse response = login("10.0.5.1", "rlright01", PASSWORD);

        assertRateLimited(response);
        assertThat(users.findByLoginId("rlright01").orElseThrow().getLastLoginAt()).isNull();
    }
}
