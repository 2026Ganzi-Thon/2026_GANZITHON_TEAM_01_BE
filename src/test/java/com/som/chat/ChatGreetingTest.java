package com.som.chat;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.engine.chat.ChatMode;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableChatEngine;
import com.som.support.TestSupportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 하루 1회 선제 인사(A_SPEC §7 말풍선 기본값: 홈 말풍선과 대화 첫 메시지에 같이 사용). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class ChatGreetingTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableChatEngine chatEngine;
    @Value("${som.state.bubble-text.insufficient}") String insufficientBubble;

    @AfterEach
    void cleanUp() {
        clock.reset();
        chatEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "grt");
    }

    private JsonNode messages(MockHttpSession s, String date) throws Exception {
        return json(objectMapper, mockMvc.perform(get("/api/v1/chat/messages?date=" + date).session(s)).andReturn())
                .path("items");
    }

    private long greetingCalls() {
        return chatEngine.requests().stream().filter(r -> r.mode() == ChatMode.GREETING).count();
    }

    @Test
    void 오늘_대화를_두_번_열어도_인사는_하나이고_engine도_한_번() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt01");

        JsonNode first = messages(s, "2026-11-11");
        JsonNode second = messages(s, "2026-11-11");

        assertThat(first).hasSize(1);
        assertThat(first.get(0).path("role").asText()).isEqualTo("SOMI");
        assertThat(second).hasSize(1);
        assertThat(greetingCalls()).isEqualTo(1);
    }

    @Test
    void 인사가_있으면_홈_말풍선이_인사_문구다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt02");
        JsonNode home = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(s)).andReturn());
        assertThat(home.path("bubbleText").asText()).isEqualTo(insufficientBubble);
        assertThat(greetingCalls()).isZero(); // 홈 조회는 인사를 만들지 않는다

        String greeting = messages(s, "2026-11-11").get(0).path("content").asText();

        JsonNode after = json(objectMapper, mockMvc.perform(get("/api/v1/state/today").session(s)).andReturn());
        assertThat(after.path("bubbleText").asText()).isEqualTo(greeting);
    }

    @Test
    void engine이_실패하면_인사를_저장하지_않고_다음_조회에서_다시_만든다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt03");
        chatEngine.fail(true);

        assertThat(messages(s, "2026-11-11")).isEmpty();

        chatEngine.fail(false);
        assertThat(messages(s, "2026-11-11")).hasSize(1);
    }

    @Test
    void 지난_날짜를_조회하면_인사를_만들지_않는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt04");

        assertThat(messages(s, "2026-11-10")).isEmpty();
        assertThat(greetingCalls()).isZero();
    }

    @Test
    void 사용자마다_인사가_따로다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt05a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "grt05b");

        messages(a, "2026-11-11");
        messages(b, "2026-11-11");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id LIKE 'grt05%' AND c.is_greeting", Integer.class)).isEqualTo(2);
        assertThat(greetingCalls()).isEqualTo(2);
    }
}
