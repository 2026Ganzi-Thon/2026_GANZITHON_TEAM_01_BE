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
import com.som.support.MutableClock;
import com.som.support.SwitchableChatEngine;
import com.som.support.TestSupportConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 대화 저장·조회·engine 중계(A_SPEC §8). 기본 설정: 사용자 원문은 engine에 보내지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ChatMessageTest {

    static final String MESSAGES = "/api/v1/chat/messages";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableChatEngine chatEngine;

    @AfterEach
    void cleanUp() {
        clock.reset();
        chatEngine.reset();
        ApiTestSupport.deleteUsers(jdbc, "cht");
    }

    private MockHttpSession ready(String loginId) throws Exception {
        return ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
    }

    private MvcResult send(MockHttpSession s, String text) throws Exception {
        return mockMvc.perform(post(MESSAGES).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", text)))).andReturn();
    }

    private JsonNode page(MockHttpSession s, String query) throws Exception {
        MvcResult result = mockMvc.perform(get(MESSAGES + query).session(s)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(objectMapper, result);
    }

    @Test
    void 메시지를_보내면_201이고_사용자_메시지와_소미_답장이_날짜별로_저장된다() throws Exception {
        MockHttpSession s = ready("cht01");

        MvcResult result = send(s, "오늘 좀 지쳤어");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(objectMapper, result);
        assertThat(body.path("userMessage").path("role").asText()).isEqualTo("USER");
        assertThat(body.path("userMessage").path("content").asText()).isEqualTo("오늘 좀 지쳤어");
        assertThat(body.path("replies")).isNotEmpty();
        assertThat(body.path("replies").get(0).path("role").asText()).isEqualTo("SOMI");
        assertThat(body.path("helpResources").isNull()).isTrue();

        JsonNode items = page(s, "?date=2026-11-11").path("items");
        assertThat(items).hasSize(3); // 선제 인사 + 사용자 + 답장
        assertThat(items.get(0).path("role").asText()).isEqualTo("SOMI");
        assertThat(items.get(1).path("role").asText()).isEqualTo("USER");
        assertThat(items.get(2).path("role").asText()).isEqualTo("SOMI");
        assertThat(items.get(0).path("threadDate").asText()).isEqualTo("2026-11-11");
    }

    @Test
    void 빈_문자열_공백_501자는_400이고_500자는_된다() throws Exception {
        MockHttpSession s = ready("cht02");
        assertThat(send(s, "").getResponse().getStatus()).isEqualTo(400);
        assertThat(send(s, "   ").getResponse().getStatus()).isEqualTo(400);
        assertThat(send(s, "가".repeat(501)).getResponse().getStatus()).isEqualTo(400);
        assertThat(send(s, "가".repeat(500)).getResponse().getStatus()).isEqualTo(201);
        assertThat(mockMvc.perform(post(MESSAGES).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hi\",\"userId\":1}")).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void engine이_실패하면_고정_문구로_답하고_사용자_메시지는_남는다() throws Exception { // 인사도 실패해 저장 안 됨
        MockHttpSession s = ready("cht03");
        chatEngine.fail(true);

        MvcResult result = send(s, "안녕");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode reply = json(objectMapper, result).path("replies").get(0);
        assertThat(reply.path("content").asText()).isEqualTo("지금은 잠시 답변이 어려워요. 호흡 루틴을 먼저 해볼까요?");
        assertThat(page(s, "?date=2026-11-11").path("items")).hasSize(2);
    }

    @Test
    void 기본_설정에서는_사용자_원문을_engine에_보내지_않는다(CapturedOutput output) throws Exception {
        MockHttpSession s = ready("cht04");
        send(s, "첫 메시지 secret-chat-5521");

        send(s, "두 번째 secret-chat-5522");

        assertThat(chatEngine.lastRequest().userText()).isNull();
        assertThat(chatEngine.lastRequest().recentMessages()).isNotEmpty()
                .allSatisfy(t -> {
                    if (t.role() == com.som.engine.chat.ChatRole.USER) {
                        assertThat(t.text()).isNull();
                    }
                });
        assertThat(output.getAll()).doesNotContain("secret-chat-55");
    }

    @Test
    void 위기로_분류되면_도움_받을_곳과_위험_표시가_붙는다() throws Exception {
        MockHttpSession s = ready("cht05");
        chatEngine.override(r -> new ChatResult("많이 힘들었죠.", RiskLevel.HIGH, List.of()));

        JsonNode body = json(objectMapper, send(s, "힘들어"));

        assertThat(body.path("helpResources")).hasSize(4);
        assertThat(body.path("replies").get(0).path("riskFlag").asBoolean()).isTrue();
    }

    @Test
    void 카탈로그에_없는_루틴_카드는_버리고_있는_카드는_카탈로그_값으로_저장한다() throws Exception {
        MockHttpSession s = ready("cht06");
        chatEngine.override(r -> new ChatResult("이거 해볼래요?", RiskLevel.NONE, List.of(
                new ChatCard(CardType.ROUTINE_RECOMMENDATION, "BREATHING", "호흡 따라하기 - 2분", null),
                new ChatCard(CardType.ROUTINE_RECOMMENDATION, "YOGA", "요가", null))));

        JsonNode replies = json(objectMapper, send(s, "추천해줘")).path("replies");

        assertThat(replies).hasSize(2);
        JsonNode card = replies.get(1);
        assertThat(card.path("messageType").asText()).isEqualTo("ROUTINE_RECOMMENDATION");
        assertThat(card.path("payload").path("routineId").asText()).isEqualTo("BREATHING");
        assertThat(card.path("payload").path("title").asText()).isEqualTo("숨 고르기");
        assertThat(card.path("payload").path("durationSec").asInt()).isEqualTo(60);
    }

    @Test
    void 커서로_다음_페이지를_읽는다() throws Exception {
        MockHttpSession s = ready("cht07");
        for (int i = 0; i < 3; i++) {
            send(s, "메시지 " + i); // 사용자 1 + 소미 1 = 2행씩, 첫 전송 전 선제 인사 1행
        }

        JsonNode first = page(s, "?date=2026-11-11&size=4");
        assertThat(first.path("items")).hasSize(4);
        String cursor = first.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        JsonNode second = page(s, "?date=2026-11-11&size=4&cursor=" + cursor);
        assertThat(second.path("items")).hasSize(3);
        assertThat(second.path("nextCursor").isNull()).isTrue();
        assertThat(second.path("items").get(0).path("id").asLong())
                .isGreaterThan(first.path("items").get(3).path("id").asLong());

        assertThat(mockMvc.perform(get(MESSAGES + "?date=2026-11-11&size=101").session(s)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(get(MESSAGES + "?date=2026-11-11&cursor=!!bad").session(s)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void 대화가_있는_날짜를_월별로_돌려준다() throws Exception {
        MockHttpSession s = ready("cht08");
        clock.set("2026-11-03T05:00:00Z");
        send(s, "3일");
        clock.set("2026-11-11T05:00:00Z");
        send(s, "11일");
        clock.set("2026-12-01T05:00:00Z");
        send(s, "12월");

        JsonNode dates = json(objectMapper, mockMvc.perform(get("/api/v1/chat/dates?month=2026-11").session(s))
                .andReturn());

        assertThat(dates).hasSize(2);
        assertThat(dates.get(0).asText()).isEqualTo("2026-11-03");
        assertThat(dates.get(1).asText()).isEqualTo("2026-11-11");
        assertThat(mockMvc.perform(get("/api/v1/chat/dates?month=2026-13").session(s)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void 다른_사용자의_대화는_날짜로도_커서로도_보이지_않는다() throws Exception {
        MockHttpSession a = ready("cht09a");
        MockHttpSession b = ready("cht09b");
        send(a, "A의 비밀");
        JsonNode aPage = page(a, "?date=2026-11-11&size=1");
        String aCursor = aPage.path("nextCursor").asText();

        assertThat(page(b, "?date=2026-11-11").path("items"))
                .allSatisfy(m -> assertThat(m.path("content").asText()).doesNotContain("A의 비밀"))
                .noneMatch(m -> m.path("role").asText().equals("USER"));
        assertThat(page(b, "?date=2026-11-11&cursor=" + aCursor).path("items"))
                .noneMatch(m -> m.path("content").asText().contains("A의 비밀"));
        clock.set("2026-11-05T05:00:00Z");
        send(a, "A만 대화한 날");
        JsonNode bDates = json(objectMapper, mockMvc.perform(get("/api/v1/chat/dates?month=2026-11").session(b))
                .andReturn());
        assertThat(bDates).noneMatch(d -> d.asText().equals("2026-11-05"));
    }

    @Test
    void 대화_마치기_API는_없다() throws Exception {
        MockHttpSession s = ready("cht10");
        assertThat(mockMvc.perform(post("/api/v1/chat/end").session(s).with(csrf())).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }
}
