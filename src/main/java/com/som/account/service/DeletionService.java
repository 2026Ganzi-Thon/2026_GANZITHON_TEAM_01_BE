package com.som.account.service;

import com.som.account.dto.DeletionStatusResponse;
import com.som.account.repository.DeletionRequestRepository;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.engine.data.EngineDataEraser;
import com.som.state.service.EngineGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * 기록 삭제·탈퇴(A_SPEC §10·§11). 순서: DB 삭제(한 트랜잭션, UserDataEraser) → 커밋 후 engine 처리물 삭제
 * (EngineGateway 경유) → 익명화된 처리 기록. engine 삭제가 실패하면 ENGINE_PENDING으로 남긴다.
 * 삭제 후 스냅샷을 다시 계산하지 않는다(지운 데이터를 engine에 다시 보내지 않게).
 */
@Service
public class DeletionService {

    static final String COMPLETED = "COMPLETED";
    static final String ENGINE_PENDING = "ENGINE_PENDING";

    private final UserRepository users;
    private final UserDataEraser eraser;
    private final EngineDataEraser engineEraser;
    private final EngineGateway gateway;
    private final DeletionRequestRepository requests;
    private final ServiceDates dates;

    public DeletionService(UserRepository users, UserDataEraser eraser, EngineDataEraser engineEraser,
                           EngineGateway gateway, DeletionRequestRepository requests, ServiceDates dates) {
        this.users = users;
        this.eraser = eraser;
        this.engineEraser = engineEraser;
        this.gateway = gateway;
        this.requests = requests;
        this.dates = dates;
    }

    public DeletionStatusResponse delete(Long userId, DeletionScope scope) {
        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        OffsetDateTime requestedAt = dates.now();
        eraser.erase(userId, scope); // 자체 트랜잭션으로 커밋된다

        // TODO(DECISION): ENGINE_PENDING 재시도 주기·방식(A_SPEC §11) — 지금은 기록만 남기고 재시도하지 않는다
        boolean engineErased = gateway.call("engine.erase", () -> {
            engineEraser.eraseSubject(subjectKey);
            return Boolean.TRUE;
        }, Boolean.TRUE::equals).isPresent();
        String status = engineErased ? COMPLETED : ENGINE_PENDING;
        OffsetDateTime completedAt = engineErased ? dates.now() : null;
        requests.insert(hash(subjectKey), scope.name(), status, requestedAt, completedAt);
        return new DeletionStatusResponse(scope.name(), status, requestedAt, completedAt);
    }

    public DeletionStatusResponse latest(Long userId) {
        String subjectKey = users.findById(userId).map(User::getSubjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        DeletionStatusResponse found = requests.findLatest(hash(subjectKey))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new DeletionStatusResponse(found.scope(), found.status(), dates.inServiceZone(found.requestedAt()),
                dates.inServiceZone(found.completedAt()));
    }

    /** subjectKey의 SHA-256(hex). 처리 기록을 사용자와 다시 잇지 않도록 원값을 저장하지 않는다. */
    static String hash(String subjectKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(subjectKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
