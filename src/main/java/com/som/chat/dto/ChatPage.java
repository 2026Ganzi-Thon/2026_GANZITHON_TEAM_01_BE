package com.som.chat.dto;

import java.util.List;

/** 날짜별 대화 한 페이지. nextCursor가 null이면 마지막 페이지. */
public record ChatPage(List<ChatMessageResponse> items, String nextCursor) {}
