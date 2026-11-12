package com.som.account.dto;

import java.time.OffsetDateTime;

/**
 * @param status      COMPLETED(DB·engine 모두 삭제) 또는 ENGINE_PENDING(DB는 삭제, engine 처리물 삭제 대기)
 * @param completedAt ENGINE_PENDING이면 null
 */
public record DeletionStatusResponse(String scope, String status, OffsetDateTime requestedAt,
                                     OffsetDateTime completedAt) {}
