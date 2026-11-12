package com.som.engine.chat;

import java.math.BigDecimal;

/** 이번 주 요약(InsightEngine 결과 중 일부). 기록이 없으면 averageLevel은 null, recordedDays는 0. */
public record WeekSummary(BigDecimal averageLevel, int recordedDays) {}
