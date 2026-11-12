package com.som.routine.service;

import com.som.routine.domain.RoutineSession;
import com.som.routine.domain.RoutineSessionStatus;
import com.som.routine.repository.RoutineSessionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 추천 입력용 수행 이력: 오늘 전 7일 동안 완료한 루틴 ID(중복 없이, 오래된 순). */
@Component
public class SessionRecentCompletions implements RecentCompletions {

    private static final int RECENT_DAYS = 7;

    private final RoutineSessionRepository sessions;

    public SessionRecentCompletions(RoutineSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> routineIds(Long userId, LocalDate today) {
        return sessions.findByUserIdAndStatusAndLocalDateBetweenOrderByLocalDate(userId,
                        RoutineSessionStatus.COMPLETED, today.minusDays(RECENT_DAYS), today.minusDays(1)).stream()
                .map(RoutineSession::getRoutineId).distinct().toList();
    }
}
