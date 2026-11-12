package com.som.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화 설정(som.chat).
 *
 * @param maxLength            입력 최대 글자 수(A_SPEC §8 [결정 필요])
 * @param recentTurns          engine에 넘기는 최근 메시지 수
 * @param sendUserTextToEngine 사용자 원문을 engine에 보낼지(A_SPEC §8 [결정 필요], 기본 false)
 * @param fallbackText         engine 실패 시 고정 문구
 */
@ConfigurationProperties(prefix = "som.chat")
public record ChatProperties(int maxLength, int recentTurns, boolean sendUserTextToEngine, String fallbackText,
                             int pageSize, int maxPageSize) {
    public ChatProperties {
        if (maxLength < 1 || recentTurns < 0 || fallbackText == null || pageSize < 1 || maxPageSize < pageSize) {
            throw new IllegalStateException("som.chat is not fully configured");
        }
    }
}
