package com.som.state.service;

import com.som.checkin.service.CheckinSubmittedEvent;
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

    public SnapshotTriggers(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckinSubmitted(CheckinSubmittedEvent event) {
        recalculate("checkin", event.userId(), event.localDate());
    }

    private void recalculate(String trigger, Long userId, LocalDate localDate) {
        try {
            snapshotService.recalculate(userId, localDate);
        } catch (RuntimeException e) {
            log.warn("snapshot recalculation after {} failed with {}", trigger, e.getClass().getSimpleName());
        }
    }
}
