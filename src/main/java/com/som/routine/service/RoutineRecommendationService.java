package com.som.routine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.checkin.domain.Checkin;
import com.som.checkin.repository.CheckinRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import com.som.routine.domain.RoutineCatalogItem;
import com.som.routine.repository.RoutineCatalogRepository;
import com.som.routine.repository.RoutineRecommendationRepository;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.repository.FatigueSnapshotRepository;
import com.som.state.service.EngineGateway;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 오늘의 루틴 추천 순서. engine(RoutineEngine) 결과만 쓰고 A가 순서를 만들지 않는다(CRITICAL).
 * - 오늘 저장된 추천이 있으면 그대로(하루 동안 고정).
 * - 없으면 engine 호출. 카탈로그 전체가 정확히 한 번씩 나온 결과만 유효하다(모르는 ID·중복·누락은 실패).
 * - 실패면 가장 최근 저장된 추천 + isStale, 그것도 없으면 503.
 */
@Service
public class RoutineRecommendationService {

    private final RoutineRecommendationRepository recommendations;
    private final RoutineCatalogRepository catalog;
    private final UserRepository users;
    private final CheckinRepository checkins;
    private final FatigueSnapshotRepository snapshots;
    private final RoutineEngine engine;
    private final EngineGateway gateway;
    private final ServiceDates dates;
    private final ObjectMapper objectMapper;
    private final RecentCompletions recentCompletions;

    public RoutineRecommendationService(RoutineRecommendationRepository recommendations,
                                        RoutineCatalogRepository catalog, UserRepository users,
                                        CheckinRepository checkins, FatigueSnapshotRepository snapshots,
                                        RoutineEngine engine, EngineGateway gateway, ServiceDates dates,
                                        ObjectMapper objectMapper, RecentCompletions recentCompletions) {
        this.recommendations = recommendations;
        this.catalog = catalog;
        this.users = users;
        this.checkins = checkins;
        this.snapshots = snapshots;
        this.engine = engine;
        this.gateway = gateway;
        this.dates = dates;
        this.objectMapper = objectMapper;
        this.recentCompletions = recentCompletions;
    }

    public TodayRecommendation today(Long userId) {
        LocalDate today = dates.today();
        Optional<RoutineRecommendationRepository.Stored> stored = recommendations.find(userId, today);
        if (stored.isPresent()) {
            return new TodayRecommendation(ids(stored.get().routineIdsJson()), false);
        }

        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        List<String> catalogIds = catalog.findAllByOrderByDisplayOrder().stream()
                .map(RoutineCatalogItem::getRoutineId).toList();
        Integer level = snapshots.findByUserIdAndLocalDate(userId, today).map(FatigueSnapshot::getLevel).orElse(null);
        String supportNeed = checkins.findByUserIdAndLocalDate(userId, today).map(Checkin::getSupportNeed)
                .map(Enum::name).orElse(null);
        RoutineRequest request = new RoutineRequest(subjectKey, today, level, supportNeed, catalogIds,
                recentCompletions.routineIds(userId, today));

        Optional<RoutineRecommendation> result = gateway.call("routine.recommend", () -> engine.recommend(request),
                r -> isValid(r, catalogIds));
        if (result.isPresent()) {
            recommendations.insertIfAbsent(userId, today, json(result.get().routineIds()), result.get().reason(),
                    dates.now());
            // 동시 첫 조회에서 다른 요청이 먼저 저장했으면 그 값을 쓴다(하루 동안 같은 순서)
            return recommendations.find(userId, today)
                    .map(s -> new TodayRecommendation(ids(s.routineIdsJson()), false))
                    .orElseThrow();
        }
        return recommendations.findLatestBefore(userId, today)
                .map(s -> new TodayRecommendation(ids(s.routineIdsJson()), true))
                .orElseThrow(() -> new ApiException(ErrorCode.ENGINE_UNAVAILABLE));
    }

    private static boolean isValid(RoutineRecommendation result, List<String> catalogIds) {
        if (result == null || result.routineIds() == null) {
            return false;
        }
        List<String> ids = result.routineIds();
        return ids.size() == catalogIds.size() && new HashSet<>(ids).size() == ids.size()
                && new HashSet<>(catalogIds).containsAll(ids);
    }

    private String json(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> ids(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
