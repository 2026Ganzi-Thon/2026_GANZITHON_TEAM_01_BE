package com.som.chat.dto;

import com.som.settings.dto.HelpResourceResponse;
import java.util.List;

/** 전송 결과: 저장한 사용자 메시지 + 소미 응답(텍스트·카드). 위기로 분류되면 helpResources가 붙고, 아니면 null. */
public record ChatTurnResponse(ChatMessageResponse userMessage, List<ChatMessageResponse> replies,
                               List<HelpResourceResponse> helpResources) {}
