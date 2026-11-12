package com.som.chat;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.engine.chat.CardType;
import com.som.engine.chat.ChatCard;
import com.som.engine.chat.ChatResult;
import com.som.engine.chat.RiskLevel;
import com.som.support.ApiTestSupport;
import com.som.support.SwitchableChatEngine;
import com.som.support.TestSupportConfig;
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

/** 추천 카드 수락/거절 기록(A_SPEC §8 [제안]). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RecommendationResponseTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SwitchableChatEngine chatEngine;

    @AfterEach
    void cleanUp() {
        chatEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "rcr");
    }

    /** 사용자 메시지를 보내고 (텍스트 답장 id, 루틴 카드 id)를 돌려준다. */
    private long[] sendWithCard(MockHttpSession s) throws Exception {
        chatEngine.override(r -> new ChatResult("이거 해볼래요?", RiskLevel.NONE, List.of(
                new ChatCard(CardType.ROUTINE_RECOMMENDATION, "BREATHING", "x", null))));
        JsonNode replies = json(objectMapper, mockMvc.perform(post("/api/v1/chat/messages").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "추천해줘")))).andReturn()).path("replies");
        return new long[] {replies.get(0).path("id").asLong(), replies.get(1).path("id").asLong()};
    }

    private MvcResult respond(MockHttpSession s, long messageId, String response) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/recommendations/" + messageId + "/response").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"" + response + "\"}")).andReturn();
    }

    @Test
    void 수락하면_기록되고_다시_보내도_같으며_다른_응답으로_바꾸면_409() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcr01");
        long card = sendWithCard(s)[1];

        MvcResult accepted = respond(s, card, "ACCEPTED");
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(objectMapper, accepted);
        assertThat(body.path("messageId").asLong()).isEqualTo(card);
        assertThat(body.path("routineId").asText()).isEqualTo("BREATHING");
        assertThat(body.path("response").asText()).isEqualTo("ACCEPTED");

        assertThat(respond(s, card, "ACCEPTED").getResponse().getStatus()).isEqualTo(200);
        assertThat(respond(s, card, "DECLINED").getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_responses WHERE message_id = ?",
                Integer.class, card)).isEqualTo(1);

        JsonNode items = json(objectMapper, mockMvc.perform(get("/api/v1/chat/messages?date=2026-11-11").session(s))
                .andReturn()).path("items");
        JsonNode cardItem = null;
        for (JsonNode item : items) {
            if (item.path("id").asLong() == card) {
                cardItem = item;
            }
        }
        assertThat(cardItem).isNotNull();
        assertThat(cardItem.path("cardResponse").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void 카드가_아닌_메시지나_없는_메시지는_404() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcr02");
        long text = sendWithCard(s)[0];

        assertThat(respond(s, text, "ACCEPTED").getResponse().getStatus()).isEqualTo(404);
        assertThat(respond(s, 999_999_999L, "ACCEPTED").getResponse().getStatus()).isEqualTo(404);
        assertThat(respond(s, text, "MAYBE").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void 다른_사용자의_카드에는_응답할_수_없다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcr03a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcr03b");
        long aCard = sendWithCard(a)[1];

        assertThat(respond(b, aCard, "DECLINED").getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_responses WHERE message_id = ?",
                Integer.class, aCard)).isZero();
    }
}
