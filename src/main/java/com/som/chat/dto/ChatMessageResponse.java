package com.som.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 대화 메시지 하나. payload는 카드 정보(TEXT면 null). content는 원문 — 화면은 텍스트로 출력해야 한다. */
public record ChatMessageResponse(Long id, LocalDate threadDate, String role, String messageType, String content,
                                  JsonNode payload, boolean riskFlag, OffsetDateTime createdAt) {

    @Override
    public String toString() {
        return "ChatMessageResponse[id=" + id + ", role=" + role + "]";
    }
}
