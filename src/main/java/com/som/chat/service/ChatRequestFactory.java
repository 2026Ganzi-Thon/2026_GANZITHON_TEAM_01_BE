package com.som.chat.service;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.chat.ChatProperties;
import com.som.chat.domain.ChatMessage;
import com.som.chat.domain.MessageRole;
import com.som.chat.domain.MessageType;
import com.som.chat.repository.ChatMessageRepository;
import com.som.checkin.domain.CheckinStatus;
import com.som.checkin.repository.CheckinRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.connection.service.SourceConsentChecker;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatRole;
import com.som.engine.chat.ChatTurn;
import com.som.engine.chat.WeekSummary;
import com.som.engine.fatigue.CheckinInput;
import com.som.state.dto.InsightResponse;
import com.som.state.service.InsightService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * ChatEngine 입력 조립(A_SPEC §8 ③): 오늘 체크인, 이번 주 요약, 최근 N개 메시지, 동의 상태만.
 * userId 대신 subjectKey. 마음 정리 기록은 넣지 않는다. 사용자 원문은 som.chat.send-user-text-to-engine일 때만.
 */
@Component
public class ChatRequestFactory {

    private final UserRepository users;
    private final CheckinRepository checkins;
    private final ChatMessageRepository messages;
    private final InsightService insights;
    private final SourceConsentChecker consents;
    private final ChatProperties properties;

    public ChatRequestFactory(UserRepository users, CheckinRepository checkins, ChatMessageRepository messages,
                              InsightService insights, SourceConsentChecker consents, ChatProperties properties) {
        this.users = users;
        this.checkins = checkins;
        this.messages = messages;
        this.insights = insights;
        this.consents = consents;
        this.properties = properties;
    }

    // TODO(DECISION): 사용자 원문을 engine(외부 LLM)에 보낼지·고지 방식(A_SPEC §8) — 기본: 보내지 않음
    public ChatRequest build(Long userId, LocalDate date, ChatMode mode, String userText) {
        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        boolean sendText = properties.sendUserTextToEngine();
        Set<String> consented = consents.consentedSources(userId).stream().map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        return new ChatRequest(subjectKey, mode, date, sendText ? userText : null, recentTurns(userId, sendText),
                checkin(userId, date), weekSummary(userId), consented);
    }

    private List<ChatTurn> recentTurns(Long userId, boolean sendText) {
        if (properties.recentTurns() == 0) {
            return List.of();
        }
        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage m : messages.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, properties.recentTurns()))) {
            if (m.getMessageType() != MessageType.TEXT) {
                continue; // 카드는 대화 맥락에서 뺀다
            }
            ChatRole role = m.getRole() == MessageRole.USER ? ChatRole.USER : ChatRole.SOMI;
            turns.add(new ChatTurn(role, role == ChatRole.USER && !sendText ? null : m.getContent()));
        }
        Collections.reverse(turns); // 오래된 순
        return turns;
    }

    private CheckinInput checkin(Long userId, LocalDate date) {
        return checkins.findByUserIdAndLocalDate(userId, date).map(c -> c.getStatus() == CheckinStatus.SKIPPED
                ? new CheckinInput(null, List.of(), null)
                : new CheckinInput(c.getMood() == null ? null : c.getMood().name(),
                        c.getStressors().stream().map(Enum::name).toList(),
                        c.getSupportNeed() == null ? null : c.getSupportNeed().name()))
                .orElse(null);
    }

    /** 이번 주 요약을 못 구하면(engine 실패) null. */
    private WeekSummary weekSummary(Long userId) {
        try {
            InsightResponse week = insights.week(userId);
            return new WeekSummary(week.averageLevel(), week.recordedDays());
        } catch (ApiException e) {
            return null;
        }
    }
}
