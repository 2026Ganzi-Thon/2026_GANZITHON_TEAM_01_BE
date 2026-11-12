package com.som.journal.dto;

import com.som.journal.domain.JournalEmotion;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** text는 원문이다. 화면은 HTML이 아닌 텍스트로 출력해야 한다(XSS). */
public record JournalResponse(Long id, LocalDate localDate, JournalEmotion emotion, String text,
                              OffsetDateTime createdAt, boolean woolTrimmedToday) {

    @Override
    public String toString() {
        return "JournalResponse[id=" + id + ", localDate=" + localDate + "]";
    }
}
