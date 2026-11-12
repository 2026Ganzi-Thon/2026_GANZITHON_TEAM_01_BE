package com.som.engine.chat;

import java.util.List;

public record ChatResult(String text, RiskLevel riskLevel, List<ChatCard> cards) {

    @Override
    public String toString() {
        return "ChatResult[riskLevel=" + riskLevel + ", cards=" + (cards == null ? 0 : cards.size()) + "]";
    }
}
