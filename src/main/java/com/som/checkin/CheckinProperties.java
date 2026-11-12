package com.som.checkin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxStressors Q2 최대 선택 개수(A_SPEC §6 [결정 필요], 기본 1)
 * @param maxRangeDays 기간 조회 최대 일수
 */
@ConfigurationProperties(prefix = "som.checkin")
public record CheckinProperties(int maxStressors, int maxRangeDays) {
    public CheckinProperties {
        if (maxStressors < 1 || maxRangeDays < 1) {
            throw new IllegalStateException("som.checkin.max-stressors and max-range-days must be >= 1");
        }
    }
}
