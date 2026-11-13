package com.som.engine.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
/**
 * 대화 카드. ROUTINE_RECOMMENDATION의 routineId는 카탈로그에 있는 값이어야 한다(A가 확인).
 * WEEK_SUMMARY는 근거가 있을 때만 만든다(근거 없는 수치 금지).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCard(CardType type, String routineId, String title, String body) {}
