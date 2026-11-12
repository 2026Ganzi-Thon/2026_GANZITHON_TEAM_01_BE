package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 회원가입 요청 제한(IP 기준). Argon2 해시 비용 때문에 대량 가입으로 서버를 느리게 만들지 못하게 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "som.rate-limit.signup.per-ip.capacity=2",
        "som.rate-limit.signup.per-ip.refill-period=1h"})
class SignupRateLimitTest {

    static final String PASSWORD = "abcde12345";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'surl%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'surl%'");
    }

    private MockHttpServletResponse signup(String ip, String loginId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup").with(csrf())
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("loginId", loginId, "password", PASSWORD, "passwordConfirm", PASSWORD))))
                .andReturn().getResponse();
    }

    @Test
    void 같은_IP에서_capacity를_넘으면_429이고_계정이_만들어지지_않는다() throws Exception {
        assertThat(signup("10.1.0.1", "surl01").getStatus()).isEqualTo(201);
        assertThat(signup("10.1.0.1", "surl02").getStatus()).isEqualTo(201);

        MockHttpServletResponse limited = signup("10.1.0.1", "surl03");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        JsonNode json = objectMapper.readTree(limited.getContentAsString());
        assertThat(json.path("error").path("code").asText()).isEqualTo(ErrorCode.RATE_LIMITED.name());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = 'surl03'", Integer.class))
                .isZero();
    }

    @Test
    void 다른_IP는_영향받지_않는다() throws Exception {
        signup("10.1.1.1", "surl11");
        signup("10.1.1.1", "surl12");
        assertThat(signup("10.1.1.1", "surl13").getStatus()).isEqualTo(429);

        assertThat(signup("10.1.1.2", "surl14").getStatus()).isEqualTo(201);
    }
}
