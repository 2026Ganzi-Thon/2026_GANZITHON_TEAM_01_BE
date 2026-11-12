package com.som.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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

/** 삭제 요청 제한(사용자 기준, A_SPEC §11). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@TestPropertySource(properties = {
        "som.rate-limit.deletion.per-user.capacity=2",
        "som.rate-limit.deletion.per-user.refill-period=1h"})
class DeletionRateLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "drl");
        jdbc.update("DELETE FROM deletion_requests");
    }

    private MockHttpServletResponse deleteData(MockHttpSession s) throws Exception {
        return mockMvc.perform(delete("/api/v1/me/data").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andReturn().getResponse();
    }

    @Test
    void 제한을_넘으면_429이고_다른_사용자는_영향받지_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "drl01a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "drl01b");
        assertThat(deleteData(a).getStatus()).isEqualTo(200);
        assertThat(deleteData(a).getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = deleteData(a);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(deleteData(b).getStatus()).isEqualTo(200);
    }
}
