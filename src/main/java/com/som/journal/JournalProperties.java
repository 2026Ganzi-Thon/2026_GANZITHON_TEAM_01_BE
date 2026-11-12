package com.som.journal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 마음 정리 입력 규칙(som.journal). 잠금(requires-all-routines)은 routine.JournalLockProperties가 읽는다.
 *
 * @param maxTextLength 자유 기록 최대 글자 수(A_SPEC §9 [결정 필요])
 * @param allowEmpty    감정·기록이 모두 비어도 완료할 수 있는지(A_SPEC §9 [결정 필요], 기본 true)
 */
@ConfigurationProperties(prefix = "som.journal")
public record JournalProperties(int maxTextLength, boolean allowEmpty) {
    public JournalProperties {
        if (maxTextLength < 1) {
            throw new IllegalStateException("som.journal.max-text-length must be >= 1");
        }
    }
}
