package com.som.state;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 홈 상태 표시 설정(som.state).
 *
 * @param bubbleText 말풍선 기본 문구 — levels는 1~4단계(4개), insufficient는 단계가 없을 때
 */
@ConfigurationProperties(prefix = "som.state")
public record StateProperties(BubbleText bubbleText) {

    public record BubbleText(List<String> levels, String insufficient) {}

    public StateProperties {
        if (bubbleText == null || bubbleText.levels() == null || bubbleText.levels().size() != 4
                || bubbleText.insufficient() == null) {
            throw new IllegalStateException("som.state.bubble-text needs 4 levels and insufficient");
        }
    }
}
