package com.som.state.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.repository.UserRepository;
import com.som.checkin.domain.Checkin;
import com.som.checkin.domain.CheckinStatus;
import com.som.checkin.repository.CheckinRepository;
import com.som.common.lock.AdvisoryLock;
import com.som.common.time.ServiceDates;
import com.som.connection.domain.DataSource;
import com.som.connection.repository.DailySignalRepository;
import com.som.connection.service.SourceConsentChecker;
import com.som.engine.fatigue.CheckinInput;
import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.SignalInput;
import com.som.state.domain.FatigueSnapshot;
import com.som.state.repository.FatigueSnapshotRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 그날 입력(체크인 + 동의한 소스의 신호)을 모아 engine으로 계산하고 스냅샷을 교체한다(A_SPEC §12).
 * - engine에는 subjectKey만 넘기고 userId·loginId·마음 정리 기록은 넘기지 않는다.
 * - 미동의 소스의 행이 DB에 남아 있어도 넘기지 않는다(CRITICAL).
 * - engine 실패면 기존 스냅샷을 is_stale=true로만 바꾸고, 없으면 아무것도 만들지 않는다(값 생성 금지).
 * engine 호출 동안 DB 트랜잭션을 잡지 않도록 읽기·쓰기를 각각 새 트랜잭션으로 나눈다
 * (커밋 후 리스너에서도 부를 수 있게 REQUIRES_NEW).
 */
@Service
public class SnapshotService {

    private static final int MAX_ATTEMPTS = 3;

    private final UserRepository users;
    private final CheckinRepository checkins;
    private final SourceConsentChecker consents;
    private final DailySignalRepository signals;
    private final FatigueSnapshotRepository snapshots;
    private final FatigueEngine engine;
    private final EngineGateway gateway;
    private final AdvisoryLock locks;
    private final ServiceDates dates;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public SnapshotService(UserRepository users, CheckinRepository checkins, SourceConsentChecker consents,
                           DailySignalRepository signals, FatigueSnapshotRepository snapshots, FatigueEngine engine,
                           EngineGateway gateway, AdvisoryLock locks, ServiceDates dates, Clock clock,
                           ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.checkins = checkins;
        this.consents = consents;
        this.signals = signals;
        this.snapshots = snapshots;
        this.engine = engine;
        this.gateway = gateway;
        this.locks = locks;
        this.dates = dates;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 재계산이 겹치면(체크인·수신·철회·삭제가 동시에) 먼저 시작한 계산이 늦게 저장될 수 있다. 그래서 저장 직전에
     * 잠금 안에서 입력을 다시 읽어, 계산에 쓴 입력과 다르면 그 결과를 버리고 새 입력으로 다시 계산한다
     * (철회·삭제된 데이터로 만든 값이 남지 않게, CRITICAL). 계속 바뀌면 MAX_ATTEMPTS 후 현재 스냅샷을 돌려준다.
     */
    public Optional<FatigueSnapshot> recalculate(Long userId, LocalDate localDate) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            FatigueRequest request = readTx.execute(status -> buildRequest(userId, localDate));
            if (request == null) {
                return Optional.empty(); // 사용자 없음(삭제됨)
            }
            Optional<FatigueResult> result = gateway.call("fatigue.calculate", () -> engine.calculate(request),
                    FatigueResultValidator::isValid);
            SaveOutcome outcome = writeTx.execute(status -> save(userId, localDate, request, result));
            if (outcome.saved()) {
                return outcome.snapshot();
            }
        }
        return readTx.execute(status -> snapshots.findByUserIdAndLocalDate(userId, localDate));
    }

    private record SaveOutcome(boolean saved, Optional<FatigueSnapshot> snapshot) {}

    private FatigueRequest buildRequest(Long userId, LocalDate localDate) {
        return users.findById(userId).map(user -> {
            Set<DataSource> consented = consents.consentedSources(userId);
            List<SignalInput> signalInputs = signals.findByUserIdAndLocalDate(userId, localDate).stream()
                    .filter(row -> consented.contains(row.source()))
                    .map(row -> new SignalInput(row.source().name(), row.metric().name(), row.value(),
                            dates.inServiceZone(row.valueAt()), row.unit(), row.hourBucket()))
                    .toList();
            CheckinInput checkin = checkins.findByUserIdAndLocalDate(userId, localDate)
                    .map(SnapshotService::toInput).orElse(null);
            Set<String> consentedNames = consented.stream().map(Enum::name)
                    .collect(Collectors.toCollection(TreeSet::new));
            return new FatigueRequest(user.getSubjectKey(), localDate, clock.getZone(), checkin, signalInputs,
                    consentedNames);
        }).orElse(null);
    }

    private static CheckinInput toInput(Checkin checkin) {
        if (checkin.getStatus() == CheckinStatus.SKIPPED) {
            return new CheckinInput(null, List.of(), null); // 건너뜀: 값 없음(피로 추정에 쓰지 않는다)
        }
        return new CheckinInput(checkin.getMood() == null ? null : checkin.getMood().name(),
                checkin.getStressors().stream().map(Enum::name).toList(),
                checkin.getSupportNeed() == null ? null : checkin.getSupportNeed().name());
    }

    private SaveOutcome save(Long userId, LocalDate localDate, FatigueRequest used, Optional<FatigueResult> result) {
        locks.lock("snapshot:" + userId + ":" + localDate);
        if (!used.equals(buildRequest(userId, localDate))) {
            return new SaveOutcome(false, Optional.empty()); // 계산 중에 입력이 바뀌었다: 버리고 다시 계산
        }
        Optional<FatigueSnapshot> existing = snapshots.findByUserIdAndLocalDate(userId, localDate);
        if (result.isEmpty()) {
            existing.ifPresent(snapshot -> snapshot.markStale(dates.now()));
            return new SaveOutcome(true, existing.map(snapshots::save));
        }
        FatigueResult r = result.get();
        FatigueSnapshot snapshot = existing.orElseGet(() -> FatigueSnapshot.of(userId, localDate));
        snapshot.replace(r.score(), r.level(), r.woolDisplayKg(), r.status(), r.statusText(), r.subText(),
                r.assetKey(), toJson(r.usedSources()), r.algorithmVersion(), r.calculatedAt(), dates.now());
        return new SaveOutcome(true, Optional.of(snapshots.save(snapshot)));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize used sources", e);
        }
    }
}
