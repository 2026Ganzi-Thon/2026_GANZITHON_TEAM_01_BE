package com.som.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key 처리(A_SPEC §13). 헤더는 선택이며, 없으면 action만 실행한다.
 * 같은 (userId, endpoint, key)는 잠금으로 직렬화하고, action과 키 저장을 한 트랜잭션에서 처리한다
 * (action이 실패하면 키도 남지 않아 다시 시도할 수 있다). 요청·응답 본문은 로그에 남기지 않는다.
 */
@Service
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyService {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;
    private final ServiceDates dates;
    private final IdempotencyProperties properties;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper objectMapper, ServiceDates dates,
                              IdempotencyProperties properties) {
        this.keys = keys;
        this.objectMapper = objectMapper;
        this.dates = dates;
        this.properties = properties;
    }

    @Transactional
    public <T> IdempotentResult<T> execute(Long userId, String endpoint, String key, Object request,
                                           Class<T> responseType, Supplier<IdempotentResult.Outcome<T>> action) {
        if (key == null) {
            IdempotentResult.Outcome<T> outcome = action.get();
            return new IdempotentResult<>(outcome.status(), outcome.body(), false);
        }
        if (!KEY.matcher(key).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key: 형식이 올바르지 않습니다");
        }
        String requestHash = sha256(toJson(request));
        OffsetDateTime now = dates.now();

        keys.lock(userId, endpoint, key);
        Optional<IdempotencyKeyRepository.StoredResponse> stored = keys.find(userId, endpoint, key);
        if (stored.isPresent() && stored.get().createdAt().isBefore(now.minus(properties.retention()))) {
            keys.delete(userId, endpoint, key); // 보존 기간이 지난 키는 새 요청으로 본다
            stored = Optional.empty();
        }
        if (stored.isPresent()) {
            if (!stored.get().requestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency-Key: 다른 요청에 이미 사용된 키입니다");
            }
            return new IdempotentResult<>(stored.get().status(), fromJson(stored.get().body(), responseType), true);
        }

        IdempotentResult.Outcome<T> outcome = action.get();
        keys.insert(userId, endpoint, key, requestHash, outcome.status(), toJson(outcome.body()), now);
        return new IdempotentResult<>(outcome.status(), outcome.body(), false);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize idempotent payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read stored idempotent response", e);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
