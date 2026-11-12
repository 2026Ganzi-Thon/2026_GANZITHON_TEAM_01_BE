package com.som.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTest {

    static final String ENDPOINT = "POST /api/v1/test";

    record Body(String value) {}

    @Autowired IdempotencyService idempotency;
    @Autowired UserRepository users;
    @Autowired ServiceDates dates;
    @Autowired JdbcTemplate jdbc;

    Long userA;
    Long userB;
    final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        userA = users.saveAndFlush(User.register("idema01", "x", UUID.randomUUID().toString(), dates.now())).getId();
        userB = users.saveAndFlush(User.register("idemb01", "x", UUID.randomUUID().toString(), dates.now())).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM idempotency_keys WHERE user_id IN (?, ?)", userA, userB);
        jdbc.update("DELETE FROM fatigue_snapshots WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE 'idem%')");
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'idem%'");
    }

    private IdempotentResult<Body> run(Long userId, String key, Object request) {
        return idempotency.execute(userId, ENDPOINT, key, request, Body.class,
                () -> new IdempotentResult.Outcome<>(201, new Body("call-" + calls.incrementAndGet())));
    }

    @Test
    void 키가_없으면_매번_실행된다() {
        run(userA, null, Map.of("a", 1));
        IdempotentResult<Body> second = run(userA, null, Map.of("a", 1));

        assertThat(calls).hasValue(2);
        assertThat(second.replayed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_keys WHERE user_id = ?", Integer.class, userA))
                .isZero();
    }

    @Test
    void 같은_키와_같은_요청이면_한_번만_실행하고_저장된_응답을_돌려준다() {
        IdempotentResult<Body> first = run(userA, "key-1", Map.of("a", 1));
        IdempotentResult<Body> second = run(userA, "key-1", Map.of("a", 1));

        assertThat(calls).hasValue(1);
        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(second.status()).isEqualTo(201);
    }

    @Test
    void 같은_키에_다른_요청이면_409() {
        run(userA, "key-2", Map.of("a", 1));

        assertThatThrownBy(() -> run(userA, "key-2", Map.of("a", 2)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(calls).hasValue(1);
    }

    @Test
    void 다른_사용자가_같은_키를_써도_새로_실행된다() {
        run(userA, "shared-key", Map.of("a", 1));
        IdempotentResult<Body> b = run(userB, "shared-key", Map.of("a", 1));

        assertThat(calls).hasValue(2);
        assertThat(b.replayed()).isFalse();
        assertThat(b.body().value()).isEqualTo("call-2");
    }

    @Test
    void 형식이_틀린_키는_400() {
        for (String bad : new String[] {"a".repeat(65), "has space", "", "키"}) {
            assertThatThrownBy(() -> run(userA, bad, Map.of()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void 실행이_실패하면_키가_저장되지_않아_다시_시도할_수_있다() {
        assertThatThrownBy(() -> idempotency.execute(userA, ENDPOINT, "key-3", Map.of(), Body.class, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        IdempotentResult<Body> retry = run(userA, "key-3", Map.of());
        assertThat(retry.replayed()).isFalse();
        assertThat(calls).hasValue(1);
    }
}
