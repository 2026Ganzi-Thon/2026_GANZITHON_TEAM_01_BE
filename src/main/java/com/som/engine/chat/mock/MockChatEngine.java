package com.som.engine.chat.mock;

import com.som.engine.chat.CardType;
import com.som.engine.chat.ChatCard;
import com.som.engine.chat.ChatEngine;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import com.som.engine.chat.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 개발·테스트용 대화 Mock(A_SPEC §12: 고정 문구 + 카드 예시). 실제 대화·위기 분류는 B 몫이다. 결정적.
 * 주간 요약 카드는 입력에 기록이 있을 때만, 입력 값만으로 만든다.
 */
@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "mock", matchIfMissing = true)
@EnableConfigurationProperties(MockChatProperties.class)
public class MockChatEngine implements ChatEngine {

    static final String GREETING = "안녕하세요, 소미예요. 오늘 하루는 어땠어요? (Mock)";
    static final String REPLY = "이야기해 줘서 고마워요. 천천히 같이 정리해 봐요. (Mock)";
    static final String CRISIS = "많이 힘들었겠어요. 지금 바로 이야기할 수 있는 곳을 알려 드릴게요. (Mock)";

    private final MockChatProperties properties;

    public MockChatEngine(MockChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatResult respond(ChatRequest request) {
        String text = request.userText();
        if (text != null && properties.crisisKeywords().stream().anyMatch(text::contains)) {
            return new ChatResult(CRISIS, RiskLevel.HIGH, List.of());
        }
        if (request.mode() == ChatMode.REPLY) {
            return new ChatResult(REPLY, RiskLevel.NONE, List.of());
        }
        List<ChatCard> cards = new ArrayList<>();
        if (request.weekSummary() != null && request.weekSummary().recordedDays() > 0) {
            cards.add(new ChatCard(CardType.WEEK_SUMMARY, null, "이번 주 기록",
                    "기록한 날 " + request.weekSummary().recordedDays() + "일 (Mock)"));
        }
        if (request.todayCheckin() != null && !request.todayCheckin().isEmpty()) {
            cards.add(new ChatCard(CardType.ROUTINE_RECOMMENDATION, "BREATHING", "숨 고르기", null));
        }
        return new ChatResult(GREETING, RiskLevel.NONE, cards);
    }
}
