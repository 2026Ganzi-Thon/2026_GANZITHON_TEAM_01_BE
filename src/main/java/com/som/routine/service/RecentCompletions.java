package com.som.routine.service;

import java.time.LocalDate;
import java.util.List;

/** 추천 입력용 최근 완료 루틴 ID(수행 이력). 구현: SessionRecentCompletions. */
public interface RecentCompletions {
    List<String> routineIds(Long userId, LocalDate today);
}
