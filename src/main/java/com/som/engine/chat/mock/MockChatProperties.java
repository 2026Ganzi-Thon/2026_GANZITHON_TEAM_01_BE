package com.som.engine.chat.mock;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param crisisKeywords Mock 위기 분류 키워드(개발·테스트용, 실제 분류 기준은 B) */
@ConfigurationProperties(prefix = "som.engine.mock.chat")
public record MockChatProperties(List<String> crisisKeywords) {
    public MockChatProperties {
        crisisKeywords = crisisKeywords == null ? List.of() : List.copyOf(crisisKeywords);
    }
}
