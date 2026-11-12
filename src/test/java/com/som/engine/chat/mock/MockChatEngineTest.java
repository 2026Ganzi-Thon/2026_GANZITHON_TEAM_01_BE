package com.som.engine.chat.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.chat.CardType;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import com.som.engine.chat.RiskLevel;
import com.som.engine.chat.WeekSummary;
import com.som.engine.fatigue.CheckinInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockChatEngineTest {

    final MockChatEngine engine = new MockChatEngine(new MockChatProperties(List.of("죽고 싶", "사라지고 싶")));

    private ChatRequest request(ChatMode mode, String text, CheckinInput checkin, WeekSummary week) {
        return new ChatRequest("s", mode, LocalDate.of(2026, 11, 11), text, List.of(), checkin, week, Set.of());
    }

    @Test
    void 인사와_답장은_고정_문구이고_결정적이다() {
        ChatResult greeting = engine.respond(request(ChatMode.GREETING, null, null, null));
        ChatResult reply = engine.respond(request(ChatMode.REPLY, "오늘 좀 힘들었어", null, null));

        assertThat(greeting.text()).isNotBlank();
        assertThat(reply.text()).isNotBlank().isNotEqualTo(greeting.text());
        assertThat(engine.respond(request(ChatMode.REPLY, "오늘 좀 힘들었어", null, null))).isEqualTo(reply);
        assertThat(reply.riskLevel()).isEqualTo(RiskLevel.NONE);
    }

    @Test
    void 위기_키워드가_있으면_HIGH() {
        assertThat(engine.respond(request(ChatMode.REPLY, "요즘 그냥 죽고 싶다", null, null)).riskLevel())
                .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void 체크인이_있으면_인사에_루틴_추천_카드가_붙는다() {
        ChatResult result = engine.respond(request(ChatMode.GREETING, null,
                new CheckinInput("TIRED", List.of(), null), null));

        assertThat(result.cards()).anySatisfy(c -> {
            assertThat(c.type()).isEqualTo(CardType.ROUTINE_RECOMMENDATION);
            assertThat(c.routineId()).isEqualTo("BREATHING");
        });
    }

    @Test
    void 주간_요약_카드는_기록이_있을_때만() {
        ChatResult none = engine.respond(request(ChatMode.GREETING, null, null, new WeekSummary(null, 0)));
        ChatResult some = engine.respond(request(ChatMode.GREETING, null, null,
                new WeekSummary(new BigDecimal("2.5"), 2)));

        assertThat(none.cards()).noneMatch(c -> c.type() == CardType.WEEK_SUMMARY);
        assertThat(some.cards()).anyMatch(c -> c.type() == CardType.WEEK_SUMMARY);
    }

    @Test
    void 사용자_원문이_없어도_동작한다() {
        assertThat(engine.respond(request(ChatMode.REPLY, null, null, null)).text()).isNotBlank();
    }
}
