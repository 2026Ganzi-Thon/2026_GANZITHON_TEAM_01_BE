package com.som.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.chat.ChatProperties;
import com.som.chat.domain.ChatMessage;
import com.som.chat.dto.ChatMessageResponse;
import com.som.chat.dto.ChatPage;
import com.som.chat.dto.ChatTurnResponse;
import com.som.chat.repository.ChatMessageRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.engine.chat.ChatEngine;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import com.som.settings.service.HelpResourceService;
import com.som.state.service.EngineGateway;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화(A_SPEC §8). 파이프라인: 길이 검증 → 사용자 메시지 저장 → ChatEngine(REPLY, EngineGateway 경유)
 * → 응답·위험 표시·카드 저장 → 반환. engine 실패면 고정 문구. 위기(HIGH)면 도움 받을 곳 목록을 붙인다.
 * 요청 제한은 컨트롤러가 먼저 건다. 대화 원문은 로그·예외 메시지에 넣지 않는다.
 */
@Service
@EnableConfigurationProperties(ChatProperties.class)
public class ChatService {

    private final ChatMessageRepository messages;
    private final ChatReplyWriter writer;
    private final ChatRequestFactory requests;
    private final ChatEngine engine;
    private final EngineGateway gateway;
    private final HelpResourceService helpResources;
    private final ServiceDates dates;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;

    public ChatService(ChatMessageRepository messages, ChatReplyWriter writer, ChatRequestFactory requests,
                       ChatEngine engine, EngineGateway gateway, HelpResourceService helpResources,
                       ServiceDates dates, ChatProperties properties, ObjectMapper objectMapper) {
        this.messages = messages;
        this.writer = writer;
        this.requests = requests;
        this.engine = engine;
        this.gateway = gateway;
        this.helpResources = helpResources;
        this.dates = dates;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // TODO(DECISION): 대화 입력 최대 길이(A_SPEC §8) — 기본 500자(som.chat.max-length)
    public ChatTurnResponse send(Long userId, String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "text: 값이 필요합니다");
        }
        if (text.codePointCount(0, text.length()) > properties.maxLength()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "text: 최대 " + properties.maxLength() + "자까지 보낼 수 있습니다");
        }
        LocalDate today = dates.today();
        OffsetDateTime now = dates.now();
        ChatMessage userMessage = writer.saveUserMessage(userId, today, text, now);

        Optional<ChatResult> result = respond(requests.build(userId, today, ChatMode.REPLY, text));
        List<ChatMessage> replies = writer.saveReplies(userId, today, result, false, dates.now());
        boolean crisis = replies.stream().anyMatch(ChatMessage::isRiskFlag);
        return new ChatTurnResponse(toResponse(userMessage), replies.stream().map(this::toResponse).toList(),
                crisis ? helpResources.all() : null);
    }

    Optional<ChatResult> respond(ChatRequest request) {
        return gateway.call("chat.respond", () -> engine.respond(request),
                r -> r != null && r.text() != null && !r.text().isBlank() && r.riskLevel() != null);
    }

    @Transactional(readOnly = true)
    public ChatPage page(Long userId, LocalDate date, String cursor, Integer size) {
        int pageSize = size == null ? properties.pageSize() : size;
        if (pageSize < 1 || pageSize > properties.maxPageSize()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "size: 1~" + properties.maxPageSize() + " 사이여야 합니다");
        }
        long afterId = decodeCursor(cursor);
        List<ChatMessage> rows = messages.findByUserIdAndThreadDateAndIdGreaterThanOrderByIdAsc(userId, date, afterId,
                PageRequest.of(0, pageSize + 1));
        boolean more = rows.size() > pageSize;
        List<ChatMessage> items = more ? rows.subList(0, pageSize) : rows;
        String next = more ? encodeCursor(items.get(items.size() - 1).getId()) : null;
        return new ChatPage(items.stream().map(this::toResponse).toList(), next);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> dates(Long userId, YearMonth month) {
        return messages.findThreadDates(userId, month.atDay(1), month.atEndOfMonth());
    }

    public ChatMessageResponse toResponse(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getThreadDate(), m.getRole().name(), m.getMessageType().name(),
                m.getContent(), payload(m.getPayload()), m.isRiskFlag(), dates.inServiceZone(m.getCreatedAt()));
    }

    private JsonNode payload(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read chat payload", e);
        }
    }

    /** 커서 = 마지막 메시지 id를 base64url로 감싼 불투명 문자열. 다른 사용자 id여도 userId 조건으로 걸러진다. */
    private static String encodeCursor(long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    private static long decodeCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0L;
        }
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (id < 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "cursor: 형식이 올바르지 않습니다");
        }
    }
}
