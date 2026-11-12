package com.som.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.chat.domain.CardResponse;
import com.som.chat.domain.ChatMessage;
import com.som.chat.domain.MessageType;
import com.som.chat.dto.CardResponseResult;
import com.som.chat.repository.ChatMessageRepository;
import com.som.chat.repository.RecommendationResponseRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.lock.AdvisoryLock;
import com.som.common.time.ServiceDates;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 카드 응답. 세션 사용자 소유의 ROUTINE_RECOMMENDATION 메시지에만 응답할 수 있고, 아니면 404
 * (다른 사용자 메시지가 있는지 드러내지 않는다). 루틴을 완료 처리하거나 점수·추천을 바꾸지 않는다.
 */
@Service
public class RecommendationResponseService {

    private final ChatMessageRepository messages;
    private final RecommendationResponseRepository responses;
    private final AdvisoryLock locks;
    private final ServiceDates dates;
    private final ObjectMapper objectMapper;

    public RecommendationResponseService(ChatMessageRepository messages, RecommendationResponseRepository responses,
                                         AdvisoryLock locks, ServiceDates dates, ObjectMapper objectMapper) {
        this.messages = messages;
        this.responses = responses;
        this.locks = locks;
        this.dates = dates;
        this.objectMapper = objectMapper;
    }

    // TODO(DECISION): 응답 변경 허용 여부(A_SPEC §8 [제안]) — 기본: 최초 응답 고정(다른 응답은 409)
    @Transactional
    public CardResponseResult respond(Long userId, Long messageId, CardResponse response) {
        ChatMessage card = messages.findByIdAndUserId(messageId, userId)
                .filter(m -> m.getMessageType() == MessageType.ROUTINE_RECOMMENDATION)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String routineId = routineId(card);
        locks.lock("card-response:" + userId + ":" + messageId);
        Optional<RecommendationResponseRepository.Stored> existing = responses.find(userId, messageId);
        if (existing.isPresent()) {
            if (existing.get().response() != response) {
                throw new ApiException(ErrorCode.CONFLICT, "response: 이미 다른 응답을 했습니다");
            }
            return new CardResponseResult(messageId, routineId, response, dates.inServiceZone(existing.get().createdAt()));
        }
        var now = dates.now();
        responses.insert(userId, messageId, routineId, response, now);
        return new CardResponseResult(messageId, routineId, response, now);
    }

    private String routineId(ChatMessage card) {
        try {
            return objectMapper.readTree(card.getPayload()).path("routineId").asText();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read card payload", e);
        }
    }
}
