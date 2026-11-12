package com.som.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.chat.ChatProperties;
import com.som.chat.domain.ChatMessage;
import com.som.chat.domain.MessageRole;
import com.som.chat.domain.MessageType;
import com.som.chat.repository.ChatMessageRepository;
import com.som.common.lock.AdvisoryLock;
import com.som.engine.chat.CardType;
import com.som.engine.chat.ChatCard;
import com.som.engine.chat.ChatResult;
import com.som.engine.chat.RiskLevel;
import com.som.routine.domain.RoutineCatalogItem;
import com.som.routine.repository.RoutineCatalogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소미 응답 저장: engine 결과(텍스트 + 카드) 또는 실패 시 고정 문구.
 * 루틴 카드는 카탈로그에 있는 routineId만 저장하고, 제목·시간은 카탈로그 값으로 채운다(engine 값을 믿지 않는다).
 */
@Component
public class ChatReplyWriter {

    private final ChatMessageRepository messages;
    private final RoutineCatalogRepository catalog;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final AdvisoryLock locks;

    public ChatReplyWriter(ChatMessageRepository messages, RoutineCatalogRepository catalog,
                           ChatProperties properties, ObjectMapper objectMapper, AdvisoryLock locks) {
        this.messages = messages;
        this.catalog = catalog;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.locks = locks;
    }

    @Transactional
    public ChatMessage saveUserMessage(Long userId, LocalDate date, String text, OffsetDateTime now) {
        return messages.saveAndFlush(ChatMessage.of(userId, date, MessageRole.USER, MessageType.TEXT, text, null,
                false, false, now));
    }

    /** engine 결과가 없으면 고정 문구 하나를 저장한다. greeting이면 첫 텍스트에 인사 표시를 단다. */
    @Transactional
    public List<ChatMessage> saveReplies(Long userId, LocalDate date, Optional<ChatResult> result, boolean greeting,
                                         OffsetDateTime now) {
        List<ChatMessage> saved = new ArrayList<>();
        if (result.isEmpty()) {
            saved.add(messages.saveAndFlush(ChatMessage.of(userId, date, MessageRole.SOMI, MessageType.TEXT,
                    properties.fallbackText(), null, false, greeting, now)));
            return saved;
        }
        ChatResult r = result.get();
        boolean risk = r.riskLevel() == RiskLevel.HIGH;
        saved.add(messages.saveAndFlush(ChatMessage.of(userId, date, MessageRole.SOMI, MessageType.TEXT, r.text(),
                null, risk, greeting, now)));
        for (ChatCard card : r.cards() == null ? List.<ChatCard>of() : r.cards()) {
            toCard(userId, date, card, now).map(messages::saveAndFlush).ifPresent(saved::add);
        }
        return saved;
    }

    /** 오늘 인사가 아직 없을 때만 저장한다(동시 요청은 잠금으로 직렬화, 하루 1개). */
    @Transactional
    public void saveGreetingIfAbsent(Long userId, LocalDate date, ChatResult result, OffsetDateTime now) {
        locks.lock("greeting:" + userId + ":" + date);
        if (messages.findByUserIdAndThreadDateAndGreetingTrue(userId, date).isEmpty()) {
            saveReplies(userId, date, Optional.of(result), true, now);
        }
    }

    private Optional<ChatMessage> toCard(Long userId, LocalDate date, ChatCard card, OffsetDateTime now) {
        if (card == null || card.type() == null) {
            return Optional.empty();
        }
        if (card.type() == CardType.ROUTINE_RECOMMENDATION) {
            return Optional.ofNullable(card.routineId()).flatMap(catalog::findById).map(item ->
                    ChatMessage.of(userId, date, MessageRole.SOMI, MessageType.ROUTINE_RECOMMENDATION,
                            item.getTitle(), json(routinePayload(item)), false, false, now));
        }
        String content = card.body() != null ? card.body() : card.title();
        if (content == null) {
            return Optional.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", card.title());
        payload.put("body", card.body());
        return Optional.of(ChatMessage.of(userId, date, MessageRole.SOMI, MessageType.WEEK_SUMMARY, content,
                json(payload), false, false, now));
    }

    private static Map<String, Object> routinePayload(RoutineCatalogItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routineId", item.getRoutineId());
        payload.put("title", item.getTitle());
        payload.put("durationSec", item.getDurationSec());
        return payload;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
