package com.som.chat.dto;

import com.som.chat.domain.CardResponse;
import java.time.OffsetDateTime;

public record CardResponseResult(Long messageId, String routineId, CardResponse response, OffsetDateTime createdAt) {}
