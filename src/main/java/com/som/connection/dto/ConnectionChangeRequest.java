package com.som.connection.dto;

/**
 * POST·DELETE /api/v1/me/connections/{source}의 바디. 받는 필드가 없다(바디 생략 가능).
 * 고지 내용(items·purpose·retention)·정책 버전·userId는 서버가 정하므로, 필드가 오면 400 VALIDATION_ERROR.
 */
public record ConnectionChangeRequest() {}
