package com.som.routine.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** 실행 기록 테이블(routine_sessions)이 생기기 전의 기본 구현: 이력 없음. */
@Component
public class NoRecentCompletions implements RecentCompletions {

    @Override
    public List<String> routineIds(Long userId, LocalDate today) {
        return List.of();
    }
}
