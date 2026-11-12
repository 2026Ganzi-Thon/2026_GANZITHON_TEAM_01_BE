package com.som.state;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param allowedDays GET /api/v1/insights?days= 허용값(A_SPEC §13: 7, 30) */
@ConfigurationProperties(prefix = "som.insights")
public record InsightProperties(List<Integer> allowedDays) {
    public InsightProperties {
        if (allowedDays == null || allowedDays.isEmpty() || allowedDays.stream().anyMatch(d -> d == null || d < 1)) {
            throw new IllegalStateException("som.insights.allowed-days must list positive values");
        }
    }
}
