package com.som.engine.chat;

import com.som.engine.insight.PeriodSignalChange;
import java.math.BigDecimal;
import java.util.List;

/** 이번 주 요약(InsightEngine 결과 중 일부). 기록이 없으면 averageLevel은 null, recordedDays는 0. */
public record WeekSummary(BigDecimal averageLevel, int recordedDays, List<PeriodSignalChange> changes) {
    public WeekSummary(BigDecimal averageLevel, int recordedDays) {
        this(averageLevel, recordedDays, List.of());
    }
}
