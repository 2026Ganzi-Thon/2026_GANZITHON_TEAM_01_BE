package com.som.engine.routine;

import java.util.List;

/** 추천 순서(루틴 ID) + 이유. */
public record RoutineRecommendation(List<String> routineIds, String reason) {}
