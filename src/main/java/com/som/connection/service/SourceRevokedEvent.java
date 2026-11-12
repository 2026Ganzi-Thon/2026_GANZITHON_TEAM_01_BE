package com.som.connection.service;

import com.som.connection.domain.DataSource;

/**
 * 선택 동의 철회 이벤트. 철회 트랜잭션 안에서 동기 발행된다(동의 이력 INSERT·연결 해제와 같은 트랜잭션).
 * 보유 데이터 삭제(명세 §5 철회 순서)는 SignalRevokeListener가 같은 트랜잭션에서, 오늘 스냅샷 재계산은
 * state.SnapshotTriggers가 커밋 후에 처리한다.
 */
public record SourceRevokedEvent(Long userId, DataSource source) {}
