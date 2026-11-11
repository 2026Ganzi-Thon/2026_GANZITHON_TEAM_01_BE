package com.som.common.time;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 서비스 시간대 설정 (som.time.zone). */
@ConfigurationProperties(prefix = "som.time")
public record TimeProperties(ZoneId zone) {}
