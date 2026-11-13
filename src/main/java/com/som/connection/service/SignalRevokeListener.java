package com.som.connection.service;

import com.som.connection.repository.DailySignalRepository;
import com.som.state.repository.FatigueSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 선택 동의 철회 시 보유 연동 데이터 삭제(명세 §5 철회 순서). 철회 트랜잭션 안에서 동기 실행된다.
 * 수신 API 활성 여부(som.signals.ingest-enabled)와 무관하게 동작한다(시드 데이터 포함).
 */
@Component
public class SignalRevokeListener {

    private final DailySignalRepository signals;
    private final boolean deleteOnRevoke;
    private final FatigueSnapshotRepository snapshots;

    // TODO(DECISION): 철회 시 보유 데이터 삭제 정책 미확정(A_SPEC §5). 값은 som.consent.delete-signals-on-revoke(누락 시 기동 실패)
    public SignalRevokeListener(DailySignalRepository signals, FatigueSnapshotRepository snapshots,
                                @Value("${som.consent.delete-signals-on-revoke}") boolean deleteOnRevoke) {
        this.signals = signals;
        this.snapshots = snapshots;
        this.deleteOnRevoke = deleteOnRevoke;
    }

    @EventListener
    public void onRevoked(SourceRevokedEvent event) {
        snapshots.removeSignalChanges(event.userId(), event.source().name());
        if (deleteOnRevoke) {
            signals.deleteByUserIdAndSource(event.userId(), event.source());
        }
    }
}
