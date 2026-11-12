package com.som.journal.dto;

import com.som.journal.domain.JournalEmotion;

/** POST /api/v1/journal. 둘 다 생략 가능. toString은 text를 남기지 않는다(로그 금지). */
public record JournalRequest(JournalEmotion emotion, String text) {

    @Override
    public String toString() {
        return "JournalRequest[emotion=" + (emotion == null ? null : "***") + ", text=" + (text == null ? null : "***")
                + "]";
    }
}
