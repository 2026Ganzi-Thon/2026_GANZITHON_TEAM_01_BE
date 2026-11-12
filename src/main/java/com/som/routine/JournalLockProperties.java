package com.som.routine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param requiresAllRoutines 마음 정리를 오늘 루틴 전부 완료 후에만 열지(A_SPEC §9, 기본 true) */
@ConfigurationProperties(prefix = "som.journal")
public record JournalLockProperties(boolean requiresAllRoutines) {}
