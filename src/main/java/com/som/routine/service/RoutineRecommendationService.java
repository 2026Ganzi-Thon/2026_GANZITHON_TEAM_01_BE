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
import com.som.engine.routine.RoutineContext;
import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import com.som.routine.domain.RoutineCatalogItem;
import com.som.routine.repository.RoutineCatalogRepository;
import com.som.routine.repository.RoutineRecommendationRepository;
import com.som.settings.dto.OnboardingAnswers;
import com.som.settings.dto.OnboardingAnswers.RecoveryStyle;
import com.som.settings.repository.OnboardingRepository;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.repository.FatigueSnapshotRepository;
import com.som.state.service.EngineGateway;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final OnboardingRepository onboarding;

    public RoutineRecommendationService(RoutineRecommendationRepository recommendations,
                                        RoutineCatalogRepository catalog, UserRepository users,
                                        CheckinRepository checkins, FatigueSnapshotRepository snapshots,
                                        RoutineEngine engine, EngineGateway gateway, ServiceDates dates,
                                        ObjectMapper objectMapper, RecentCompletions recentCompletions, OnboardingRepository onboarding) {
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
        this.onboarding = onboarding;
    }

    public TodayRecommendation today(Long userId) {
        LocalDate today = dates.today();
        Optional<RoutineRecommendationRepository.Stored> stored = recommendations.find(userId, today);
        if (stored.isPresent()) {
            return new TodayRecommendation(ids(stored.get().routineIdsJson()), false);
        }

        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        RoutineContext context = context(userId, today);
        List<String> catalogIds = context.catalogIds();
        Optional<Checkin> checkin = checkins.findByUserIdAndLocalDate(userId, today);
        String supportNeed = checkin.map(Checkin::getSupportNeed).map(Enum::name).orElse(null);
        String mood = checkin.map(Checkin::getMood).map(Enum::name).orElse(null);
        RoutineRequest request = new RoutineRequest(subjectKey, today, context.level(), supportNeed, catalogIds,
                context.completedRecently(), mood, context.catalogDurations(),
                context.preferredRoutineIds(), context.availableSeconds());

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

    public RoutineContext context(Long userId, LocalDate date) {
        List<RoutineCatalogItem> items = catalog.findAllByOrderByDisplayOrder();
        Integer level = snapshots.findByUserIdAndLocalDate(userId, date).map(FatigueSnapshot::getLevel).orElse(null);
        var answers = onboarding.find(userId).orElseGet(OnboardingAnswers::empty);
        var styles = answers.recoveryStyles() == null ? java.util.Set.<RecoveryStyle>of() : answers.recoveryStyles();
        var preferred = items.stream().map(RoutineCatalogItem::getRoutineId)
                .filter(id -> switch (id) {
                    case "BREATHING" -> styles.contains(RecoveryStyle.QUIET_ALONE);
                    case "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH" -> styles.contains(RecoveryStyle.MOVE_BODY);
                    default -> false;
                }).toList();
        Integer seconds = answers.availableMinutes() == null ? null : answers.availableMinutes() * 60;
        return new RoutineContext(level, items.stream().map(RoutineCatalogItem::getRoutineId).toList(),
                recentCompletions.routineIds(userId, date), items.stream().collect(Collectors.toUnmodifiableMap(
                        RoutineCatalogItem::getRoutineId, RoutineCatalogItem::getDurationSec)), preferred, seconds);
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
