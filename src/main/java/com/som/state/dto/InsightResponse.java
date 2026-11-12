package com.som.state.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 기간 기록(이번 주 · 최근 7/30일). days는 기간의 모든 날짜이고 기록이 없는 날은 null이다.
 * 평균·기록일·요약·가장 무거운 날·주간 털 무게는 InsightEngine 결과 그대로다.
 *
 * @param woolWeightKg 기간 털 무게(주간). 일별 값은 days[].woolDisplayKg — 둘 중 무엇을 쓸지 B 합의 대기(A_SPEC §7)
 * @param levelText    기간의 마지막 유효 기록 기준 상태 문구
 */
public record InsightResponse(LocalDate from, LocalDate to, List<DayLevel> days, BigDecimal averageLevel,
                              int recordedDays, String changeSummary, LocalDate heaviestDate, String heaviestReason,
                              BigDecimal woolWeightKg, String levelText) {

    public record DayLevel(LocalDate localDate, Integer level, BigDecimal woolDisplayKg) {}
}
