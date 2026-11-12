package com.som.chat.dto;

import com.som.chat.domain.CardResponse;
import jakarta.validation.constraints.NotNull;

public record CardResponseRequest(@NotNull CardResponse response) {}
