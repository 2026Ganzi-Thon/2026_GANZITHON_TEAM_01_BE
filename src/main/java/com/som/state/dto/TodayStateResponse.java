package com.som.state.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/v1/state/today (A_SPEC §7 [제안] 필드). 값은 engine 스냅샷 그대로이며 근거가 없으면 null이다. */
public record TodayStateResponse(LocalDate localDate, Integer score, Integer level, String status,
                                 String statusText, String subText, String assetKey, BigDecimal woolDisplayKg,
                                 String bubbleText, List<String> usedSources, String algorithmVersion,
                                 OffsetDateTime calculatedAt,
                                 @JsonProperty("isStale") boolean isStale,
                                 boolean woolTrimmedToday) {}
