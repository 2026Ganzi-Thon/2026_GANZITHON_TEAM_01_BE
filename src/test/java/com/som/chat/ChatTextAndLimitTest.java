package com.som.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.SwitchableChatEngine;
import com.som.support.TestSupportConfig;
import java.util.Map;
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

/** 원문 전송을 켠 설정(위기 분류 Mock 경로)과 대화 요청 제한. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@TestPropertySource(properties = {
        "som.chat.send-user-text-to-engine=true",
        "som.rate-limit.chat.per-user-minute.capacity=3",
        "som.rate-limit.chat.per-user-minute.refill-period=1h"})
class ChatTextAndLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SwitchableChatEngine chatEngine;

    @AfterEach
    void cleanUp() {
        chatEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "ctl");
    }

    private MockHttpServletResponse send(MockHttpSession s, String text) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/messages").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", text)))).andReturn().getResponse();
    }

    @Test
    void 원문_전송이_켜져_있으면_Mock_위기_분류로_도움_받을_곳이_붙는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ctl01");

        MockHttpServletResponse response = send(s, "요즘 죽고 싶다는 생각이 들어");

        assertThat(chatEngine.lastRequest().userText()).isEqualTo("요즘 죽고 싶다는 생각이 들어");
        assertThat(objectMapper.readTree(response.getContentAsString()).path("helpResources")).hasSize(4);
    }

    @Test
    void 분당_제한을_넘으면_429이고_다른_사용자는_영향받지_않는다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ctl02a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ctl02b");
        for (int i = 0; i < 3; i++) {
            assertThat(send(a, "안녕 " + i).getStatus()).isEqualTo(201);
        }

        MockHttpServletResponse limited = send(a, "또");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(objectMapper.readTree(limited.getContentAsString()).path("error").path("code").asText())
                .isEqualTo("RATE_LIMITED");
        assertThat(send(b, "안녕").getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id = 'ctl02a' AND c.role = 'USER'", Integer.class)).isEqualTo(3);
    }
}
