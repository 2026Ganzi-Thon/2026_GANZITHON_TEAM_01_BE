package com.som.engine.insight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 기간 요약 결과. 기록일이 0이면 averageLevel·heaviestDate·woolWeightKg는 null이다(0으로 채우지 않는다).
 *
 * @param woolWeightKg 기간(주간) 털 무게 — 주간/일별 중 무엇을 쓸지 B 합의 대기(A_SPEC §7)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightResult(BigDecimal averageLevel, int recordedDays, String changeSummary,
                            LocalDate heaviestDate, String heaviestReason, BigDecimal woolWeightKg,
                            String statusText, List<PeriodSignalChange> changes) {
    public InsightResult(BigDecimal averageLevel, int recordedDays, String changeSummary,
                         LocalDate heaviestDate, String heaviestReason, BigDecimal woolWeightKg) {
        this(averageLevel, recordedDays, changeSummary, heaviestDate, heaviestReason, woolWeightKg, null, List.of());
    }
}
