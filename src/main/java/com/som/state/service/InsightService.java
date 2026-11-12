package com.som.state.service;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.DateRange;
import com.som.common.time.ServiceDates;
import com.som.engine.fatigue.FatigueStatus;
import com.som.engine.insight.DailyState;
import com.som.engine.insight.InsightEngine;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.dto.InsightResponse;
import com.som.state.repository.FatigueSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 기간 기록. 스냅샷에서 날짜별 단계를 모으고(없거나 INSUFFICIENT_DATA면 null), 요약은 InsightEngine에 맡긴다.
 * A에서 평균·기록일·가장 무거운 날을 계산하지 않는다(CRITICAL). engine 실패면 503(일시 조회 제한, A_SPEC §12).
 */
@Service
public class InsightService {

    private final FatigueSnapshotRepository snapshots;
    private final UserRepository users;
    private final InsightEngine engine;
    private final EngineGateway gateway;
    private final ServiceDates dates;

    public InsightService(FatigueSnapshotRepository snapshots, UserRepository users, InsightEngine engine,
                          EngineGateway gateway, ServiceDates dates) {
        this.snapshots = snapshots;
        this.users = users;
        this.engine = engine;
        this.gateway = gateway;
        this.dates = dates;
    }

    /** 이번 주 월~일(KST). */
    public InsightResponse week(Long userId) {
        return summarize(userId, dates.currentWeek());
    }

    /** 오늘 포함 최근 N일. */
    public InsightResponse recent(Long userId, int days) {
        return summarize(userId, dates.recentDays(days));
    }

    private InsightResponse summarize(Long userId, DateRange range) {
        Loaded loaded = load(userId, range);
        InsightRequest request = new InsightRequest(loaded.subjectKey(), range.start(), range.end(), loaded.days());
        InsightResult result = gateway.call("insight.summarize", () -> engine.summarize(request),
                        InsightService::isValid)
                .orElseThrow(() -> new ApiException(ErrorCode.ENGINE_UNAVAILABLE));
        List<InsightResponse.DayLevel> days = loaded.days().stream()
                .map(d -> new InsightResponse.DayLevel(d.localDate(), d.level(), d.woolDisplayKg())).toList();
        // TODO(DECISION): 주간 단계 문구 출처 B 합의 대기(A_SPEC §7) — engine 결과에 없어 levelText는 null
        return new InsightResponse(range.start(), range.end(), days, result.averageLevel(), result.recordedDays(),
                result.changeSummary(), result.heaviestDate(), result.heaviestReason(), result.woolWeightKg(), null);
    }

    private record Loaded(String subjectKey, List<DailyState> days) {}

    private Loaded load(Long userId, DateRange range) {
        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        Map<LocalDate, FatigueSnapshot> byDate = snapshots
                .findByUserIdAndLocalDateBetweenOrderByLocalDate(userId, range.start(), range.end()).stream()
                .collect(Collectors.toMap(FatigueSnapshot::getLocalDate, Function.identity()));
        List<DailyState> days = range.days().stream().map(date -> {
            FatigueSnapshot s = byDate.get(date);
            if (s == null || s.getStatus() != FatigueStatus.CALCULATED) {
                return new DailyState(date, null, null, null);
            }
            return new DailyState(date, s.getLevel(), s.getScore(), s.getWoolDisplayKg());
        }).toList();
        return new Loaded(subjectKey, days);
    }

    /** 기록일이 0이면 평균 등이 null이어야 하고, 기록일은 0 이상이어야 한다. */
    private static boolean isValid(InsightResult result) {
        if (result == null || result.recordedDays() < 0) {
            return false;
        }
        return result.recordedDays() > 0 || (result.averageLevel() == null && result.heaviestDate() == null
                && result.woolWeightKg() == null);
    }
}
