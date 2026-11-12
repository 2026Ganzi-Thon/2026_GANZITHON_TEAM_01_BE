package com.som.routine.service;

import java.util.List;

/** 오늘 추천 순서. engine이 실패해 이전 날 추천을 쓰면 isStale=true. */
public record TodayRecommendation(List<String> routineIds, boolean isStale) {}
