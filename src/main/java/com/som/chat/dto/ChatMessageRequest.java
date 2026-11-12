package com.som.chat.dto;

/** POST /api/v1/chat/messages. toString은 원문을 남기지 않는다(로그 금지). */
public record ChatMessageRequest(String text) {

    @Override
    public String toString() {
        return "ChatMessageRequest[text=" + (text == null ? null : "***") + "]";
    }
}
