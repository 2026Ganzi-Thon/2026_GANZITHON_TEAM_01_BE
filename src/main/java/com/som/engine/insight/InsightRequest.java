package com.som.engine.insight;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 기간 요약 입력. days는 from~to 모든 날짜를 오름차순으로 담고, 기록이 없는 날은 값이 null이다. */
public record InsightRequest(String subjectKey, LocalDate from, LocalDate to, List<DailyState> days,
                             Set<String> consentedSources) {
    public InsightRequest(String subjectKey, LocalDate from, LocalDate to, List<DailyState> days) {
        this(subjectKey, from, to, days, Set.of());
    }

    @Override
    public String toString() {
        return "InsightRequest[from=" + from + ", to=" + to + "]";
    }
}
