package com.som.routine.service;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.lock.AdvisoryLock;
import com.som.common.time.DateRange;
import com.som.common.time.ServiceDates;
import com.som.routine.JournalLockProperties;
import com.som.routine.RoutineProperties;
import com.som.routine.domain.RoutineCatalogItem;
import com.som.routine.domain.RoutineSession;
import com.som.routine.domain.RoutineSessionStatus;
import com.som.routine.dto.RoutineSessionResponse;
import com.som.routine.dto.RoutinesTodayResponse;
import com.som.routine.repository.RoutineCatalogRepository;
import com.som.routine.repository.RoutineSessionRepository;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.repository.FatigueSnapshotRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 루틴 실행(시작·완료·건너뜀)과 루틴-1 화면. 사용자·루틴·날짜당 1행이라 중복 완료해도 기록이 늘지 않는다.
 * 전이: 없음/STARTED/SKIPPED → 어느 상태로든, COMPLETED는 최종(다시 complete는 같은 응답, start·skip은 409).
 * 루틴 완료는 피로 점수·단계를 바꾸지 않는다(CRITICAL) — 스냅샷을 건드리지 않는다.
 */
@Service
@EnableConfigurationProperties({RoutineProperties.class, JournalLockProperties.class})
public class RoutineSessionService {

    public enum Action { START, COMPLETE, SKIP }

    private final RoutineSessionRepository sessions;
    private final RoutineCatalogRepository catalog;
    private final RoutineRecommendationService recommendations;
    private final FatigueSnapshotRepository snapshots;
    private final AdvisoryLock locks;
    private final ServiceDates dates;
    private final RoutineProperties properties;
    private final JournalLockProperties journalLock;

    public RoutineSessionService(RoutineSessionRepository sessions, RoutineCatalogRepository catalog,
                                 RoutineRecommendationService recommendations, FatigueSnapshotRepository snapshots,
                                 AdvisoryLock locks, ServiceDates dates, RoutineProperties properties,
                                 JournalLockProperties journalLock) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.recommendations = recommendations;
        this.snapshots = snapshots;
        this.locks = locks;
        this.dates = dates;
        this.properties = properties;
        this.journalLock = journalLock;
    }

    @Transactional
    public RoutineSessionResponse act(Long userId, String routineId, Action action) {
        if (catalog.findById(routineId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        LocalDate today = dates.today();
        OffsetDateTime now = dates.now();
        locks.lock("routine:" + userId + ":" + routineId + ":" + today);
        Optional<RoutineSession> existing = sessions.findByUserIdAndRoutineIdAndLocalDate(userId, routineId, today);
        if (existing.isPresent() && existing.get().getStatus() == RoutineSessionStatus.COMPLETED) {
            if (action == Action.COMPLETE) {
                return toResponse(existing.get()); // 중복 완료: 기록·시각 그대로
            }
            throw new ApiException(ErrorCode.CONFLICT, "routineId: 이미 완료한 루틴입니다");
        }
        RoutineSession session = existing.orElseGet(() -> RoutineSession.of(userId, routineId, today));
        if (existing.isPresent() && existing.get().getStatus() == statusOf(action)) {
            return toResponse(session); // 같은 상태 요청 반복
        }
        switch (action) {
            case START -> session.start(now);
            case COMPLETE -> session.complete(now);
            case SKIP -> session.skip(now);
        }
        return toResponse(sessions.saveAndFlush(session));
    }

    public RoutinesTodayResponse today(Long userId) {
        LocalDate today = dates.today();
        TodayRecommendation recommendation = recommendations.today(userId);
        Map<String, RoutineCatalogItem> items = catalog.findAllByOrderByDisplayOrder().stream()
                .collect(Collectors.toMap(RoutineCatalogItem::getRoutineId, Function.identity()));
        Map<String, RoutineSessionStatus> statuses = sessions.findByUserIdAndLocalDate(userId, today).stream()
                .collect(Collectors.toMap(RoutineSession::getRoutineId, RoutineSession::getStatus));

        List<RoutinesTodayResponse.RoutineItem> routines = recommendation.routineIds().stream()
                .map(items::get)
                .map(item -> new RoutinesTodayResponse.RoutineItem(item.getRoutineId(), item.getTitle(),
                        item.getSubtitle(), item.getInstruction(), item.getDurationSec(), item.getAssetKey(),
                        Optional.ofNullable(statuses.get(item.getRoutineId())).map(Enum::name).orElse("NOT_STARTED")))
                .toList();
        Integer level = snapshots.findByUserIdAndLocalDate(userId, today).map(FatigueSnapshot::getLevel).orElse(null);
        return new RoutinesTodayResponse(today, level, week(userId, today), routines,
                journalUnlocked(userId, today), recommendation.isStale());
    }

    /** 마음 정리 잠금 해제 여부(서버 계산, A_SPEC §9). */
    @Transactional(readOnly = true)
    public boolean journalUnlocked(Long userId, LocalDate date) {
        if (!journalLock.requiresAllRoutines()) {
            return true;
        }
        long completed = sessions.findByUserIdAndLocalDate(userId, date).stream()
                .filter(s -> s.getStatus() == RoutineSessionStatus.COMPLETED).count();
        return completed >= catalog.findAllByOrderByDisplayOrder().size();
    }

    // TODO(DECISION): '이행' 기준(A_SPEC §9) — 기본: 그날 완료 1개 이상(som.routine.done-min-completed)
    private List<RoutinesTodayResponse.DayDone> week(Long userId, LocalDate today) {
        DateRange week = dates.currentWeek();
        Map<LocalDate, Long> completedByDate = sessions.findByUserIdAndLocalDateBetween(userId, week.start(), week.end())
                .stream().filter(s -> s.getStatus() == RoutineSessionStatus.COMPLETED)
                .collect(Collectors.groupingBy(RoutineSession::getLocalDate, Collectors.counting()));
        return week.days().stream()
                .map(d -> new RoutinesTodayResponse.DayDone(d, d.isAfter(today) ? null
                        : completedByDate.getOrDefault(d, 0L) >= properties.doneMinCompleted()))
                .toList();
    }

    private static RoutineSessionStatus statusOf(Action action) {
        return switch (action) {
            case START -> RoutineSessionStatus.STARTED;
            case COMPLETE -> RoutineSessionStatus.COMPLETED;
            case SKIP -> RoutineSessionStatus.SKIPPED;
        };
    }

    private RoutineSessionResponse toResponse(RoutineSession session) {
        return new RoutineSessionResponse(session.getRoutineId(), session.getLocalDate(), session.getStatus(),
                dates.inServiceZone(session.getFinishedAt()));
    }
}
