package com.som.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param defaultEnabled 설정 행이 없을 때 알림 활성 여부 */
@ConfigurationProperties(prefix = "som.notification")
public record NotificationProperties(boolean defaultEnabled) {}
