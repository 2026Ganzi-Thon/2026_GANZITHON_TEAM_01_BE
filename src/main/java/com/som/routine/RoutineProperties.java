package com.som.routine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param doneMinCompleted 그날을 '이행'으로 볼 최소 완료 수(A_SPEC §9 [결정 필요], 기본 1)
 */
@ConfigurationProperties(prefix = "som.routine")
public record RoutineProperties(int doneMinCompleted) {
    public RoutineProperties {
        if (doneMinCompleted < 1) {
            throw new IllegalStateException("som.routine.done-min-completed must be >= 1");
        }
    }
}
