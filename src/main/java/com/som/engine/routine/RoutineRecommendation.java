package com.som.engine.routine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 추천 순서(루틴 ID) + 이유. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutineRecommendation(List<String> routineIds, String reason) {}
