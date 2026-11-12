package com.som.state.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.state.StateProperties;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.dto.TodayStateResponse;
import com.som.state.repository.FatigueSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 홈 오늘 상태. 오늘 스냅샷이 없으면 한 번 계산해서 만든다(입력이 없으면 engine이 INSUFFICIENT_DATA를 준다).
 * 그것도 실패하면 503 — 기본 상태(0점·1단계)를 만들지 않는다.
 */
@Service
@EnableConfigurationProperties(StateProperties.class)
public class StateService {

    private final FatigueSnapshotRepository snapshots;
    private final SnapshotService snapshotService;
    private final WoolTrimQuery woolTrims;
    private final ServiceDates dates;
    private final StateProperties properties;
    private final ObjectMapper objectMapper;

    public StateService(FatigueSnapshotRepository snapshots, SnapshotService snapshotService, WoolTrimQuery woolTrims,
                        ServiceDates dates, StateProperties properties, ObjectMapper objectMapper) {
        this.snapshots = snapshots;
        this.snapshotService = snapshotService;
        this.woolTrims = woolTrims;
        this.dates = dates;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TodayStateResponse today(Long userId) {
        LocalDate today = dates.today();
        FatigueSnapshot snapshot = snapshots.findByUserIdAndLocalDate(userId, today)
                .or(() -> snapshotService.recalculate(userId, today))
                .orElseThrow(() -> new ApiException(ErrorCode.ENGINE_UNAVAILABLE));
        return new TodayStateResponse(today, snapshot.getScore(), snapshot.getLevel(), snapshot.getStatus().name(),
                snapshot.getStatusText(), snapshot.getSubText(), snapshot.getAssetKey(), snapshot.getWoolDisplayKg(),
                bubbleText(snapshot.getLevel()), usedSources(snapshot.getUsedSources()),
                snapshot.getAlgorithmVersion(), dates.inServiceZone(snapshot.getCalculatedAt()), snapshot.isStale(),
                woolTrims.trimmedOn(userId, today));
    }

    // TODO(DECISION): 말풍선 출처 B 합의 대기(A_SPEC §7) — 3단계 선제 인사 결과로 대체 예정. 지금은 단계별 고정 문구(설정값)
    private String bubbleText(Integer level) {
        return level == null ? properties.bubbleText().insufficient() : properties.bubbleText().levels().get(level - 1);
    }

    private List<String> usedSources(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read used sources", e);
        }
    }
}
