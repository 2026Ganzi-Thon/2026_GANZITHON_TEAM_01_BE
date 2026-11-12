package com.som.settings.dto;

import com.som.settings.domain.NotificationSlot;

/** @param time "HH:mm" 또는 null */
public record NotificationSettingsResponse(boolean enabled, NotificationSlot slot, String time) {}
