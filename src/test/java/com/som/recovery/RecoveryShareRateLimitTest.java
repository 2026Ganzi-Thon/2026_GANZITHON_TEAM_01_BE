package com.som.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 익명 공유 글쓰기 요청 제한(사용자 기준). 조회는 제한하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@TestPropertySource(properties = {
        "som.rate-limit.recovery-share.per-user.capacity=2",
        "som.rate-limit.recovery-share.per-user.refill-period=1h"})
class RecoveryShareRateLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "rsl");
    }

    private MockHttpServletResponse share(MockHttpSession s) throws Exception {
        return mockMvc.perform(post("/api/v1/recovery-shares").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"잠깐 걷기\"}"))
                .andReturn().getResponse();
    }

    @Test
    void 제한을_넘으면_429이고_저장되지_않으며_다른_사용자는_영향받지_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rsl01a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rsl01b");
        assertThat(share(a).getStatus()).isEqualTo(201);
        assertThat(share(a).getStatus()).isEqualTo(201);

        MockHttpServletResponse limited = share(a);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_shares r JOIN users u ON u.id = r.user_id "
                + "WHERE u.login_id = 'rsl01a'", Integer.class)).isEqualTo(2);
        assertThat(share(b).getStatus()).isEqualTo(201);
        assertThat(mockMvc.perform(get("/api/v1/recovery-shares").session(a)).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
    }
}
