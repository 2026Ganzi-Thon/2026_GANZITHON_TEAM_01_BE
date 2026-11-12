package com.som.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import com.som.connection.dto.SignalIngestRequest;
import com.som.connection.service.ConnectionService;
import com.som.connection.service.SignalService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동의 철회와 수신이 겹칠 때 철회된 소스의 데이터가 남지 않아야 한다(CRITICAL: 미동의 데이터 저장 금지).
 * 철회 트랜잭션이 커밋되기 전에 수신이 시작돼도, 수신은 철회 커밋을 기다린 뒤 미동의로 거부돼야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SignalRevokeRaceTest {

    @Autowired UserRepository users;
    @Autowired ConnectionService connectionService;
    @Autowired SignalService signalService;
    @Autowired ServiceDates dates;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        String ids = "(SELECT id FROM users WHERE login_id LIKE 'race%')";
        jdbc.update("DELETE FROM daily_signals WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM connections WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM user_consents WHERE user_id IN " + ids);
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'race%'");
    }

    @Test
    void 철회_커밋_전에_시작된_수신은_철회를_기다린_뒤_거부되고_데이터가_남지_않는다() throws Exception {
        Long userId = users.saveAndFlush(User.register("race01", "x", UUID.randomUUID().toString(), dates.now()))
                .getId();
        connectionService.connect(userId, DataSource.STEPS);
        SignalIngestRequest request = new SignalIngestRequest(DataSource.STEPS, dates.today(),
                List.of(new SignalIngestRequest.Entry(Metric.STEPS_DAILY, new BigDecimal("1234"), null, null)));

        CountDownLatch revokedButNotCommitted = new CountDownLatch(1);
        CountDownLatch releaseRevoke = new CountDownLatch(1);
        CompletableFuture<Void> revoke = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            connectionService.revoke(userId, DataSource.STEPS);
            revokedButNotCommitted.countDown();
            await(releaseRevoke);
        }));
        assertThat(revokedButNotCommitted.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<?> ingest = CompletableFuture.supplyAsync(() -> signalService.ingest(userId, request));
        try {
            ingest.get(1, TimeUnit.SECONDS); // 잠금이 없으면 여기서 바로 저장이 끝난다
        } catch (TimeoutException expected) {
            // 철회 트랜잭션을 기다리는 중
        } catch (ExecutionException ignored) {
            // 결과는 아래에서 검증
        }
        releaseRevoke.countDown();
        revoke.get(10, TimeUnit.SECONDS);

        Throwable thrown = catchThrowable(() -> ingest.get(10, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause()).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown.getCause()).code()).isEqualTo(ErrorCode.SOURCE_NOT_CONSENTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_signals WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
