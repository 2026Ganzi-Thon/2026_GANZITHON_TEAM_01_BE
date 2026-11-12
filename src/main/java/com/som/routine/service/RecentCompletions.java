package com.som.routine.service;

import java.time.LocalDate;
import java.util.List;

/** 추천 입력용 최근 7일 완료 루틴 ID(수행 이력). routine_sessions가 생기면 실제 구현으로 바꾼다. */
public interface RecentCompletions {
    List<String> routineIds(Long userId, LocalDate today);
}
