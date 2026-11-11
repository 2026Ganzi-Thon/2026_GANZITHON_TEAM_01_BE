package com.som.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 요청 ID 설정 (som.request-id). 헤더 이름과 길이 상한은 yml에서만 정한다. */
@ConfigurationProperties(prefix = "som.request-id")
public record RequestIdProperties(String header, int maxLength) {}
