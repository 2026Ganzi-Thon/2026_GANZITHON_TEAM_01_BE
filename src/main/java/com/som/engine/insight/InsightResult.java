package com.som.engine.insight;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기간 요약 결과. 기록일이 0이면 averageLevel·heaviestDate·woolWeightKg는 null이다(0으로 채우지 않는다).
 *
 * @param woolWeightKg 기간(주간) 털 무게 — 주간/일별 중 무엇을 쓸지 B 합의 대기(A_SPEC §7)
 */
public record InsightResult(BigDecimal averageLevel, int recordedDays, String changeSummary,
                            LocalDate heaviestDate, String heaviestReason, BigDecimal woolWeightKg) {}
