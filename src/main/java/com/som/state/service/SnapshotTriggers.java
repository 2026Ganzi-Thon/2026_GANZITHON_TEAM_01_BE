package com.som.state.service;

import com.som.checkin.service.CheckinSubmittedEvent;
import com.som.common.time.ServiceDates;
import com.som.connection.service.SignalsIngestedEvent;
import com.som.connection.service.SourceRevokedEvent;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 입력이 바뀐 뒤 그날 스냅샷을 다시 계산한다. 커밋 후(AFTER_COMMIT)에만 실행해서
 * 커밋 전 데이터로 계산하지 않고, 계산 실패가 원래 요청을 실패시키지 않게 한다.
 */
@Component
public class SnapshotTriggers {

    private static final Logger log = LoggerFactory.getLogger(SnapshotTriggers.class);

    private final SnapshotService snapshotService;
    private final ServiceDates dates;

    public SnapshotTriggers(SnapshotService snapshotService, ServiceDates dates) {
        this.snapshotService = snapshotService;
        this.dates = dates;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckinSubmitted(CheckinSubmittedEvent event) {
        recalculate("checkin", event.userId(), event.localDate());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalsIngested(SignalsIngestedEvent event) {
        recalculate("signals", event.userId(), event.localDate());
        LocalDate today = dates.today();
        if (event.localDate().isBefore(today)
                && !event.localDate().isBefore(today.minusDays(SnapshotService.BASELINE_DAYS))) {
            try {
                snapshotService.refreshExisting(event.userId(), today);
            } catch (RuntimeException e) {
                log.warn("snapshot recalculation after signal history failed with {}", e.getClass().getSimpleName());
            }
        }
    }

    /** 철회된 소스는 SnapshotService가 이미 제외한다. 오늘 상태에서 바로 빠지도록 오늘만 다시 계산한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSourceRevoked(SourceRevokedEvent event) {
        recalculate("revoke", event.userId(), dates.today());
    }

    private void recalculate(String trigger, Long userId, LocalDate localDate) {
        try {
            snapshotService.recalculate(userId, localDate);
        } catch (RuntimeException e) {
            log.warn("snapshot recalculation after {} failed with {}", trigger, e.getClass().getSimpleName());
        }
    }
}
