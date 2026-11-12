package com.som.state.service;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * engine 호출 경계(ARCHITECTURE: 타임아웃·재시도 상한). A의 서비스는 engine 인터페이스를 직접 부르지 않고 이것을 쓴다.
 * 실패·타임아웃·검증 실패는 Optional.empty()이고 예외를 밖으로 던지지 않는다. 기본값을 만들어 채우지 않는다.
 * 로그에는 operation·시도 횟수·예외 클래스 이름만 남긴다(요청·응답 내용, subjectKey 금지).
 */
@Component
@EnableConfigurationProperties(EngineProperties.class)
public class EngineGateway {

    private static final Logger log = LoggerFactory.getLogger(EngineGateway.class);

    private final EngineProperties properties;
    private final ExecutorService executor;

    public EngineGateway(EngineProperties properties) {
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(properties.poolSize(), daemonThreads());
    }

    public <T> Optional<T> call(String operation, Supplier<T> call, Predicate<T> valid) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            Future<T> future = executor.submit(call::get);
            try {
                T result = future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
                if (valid.test(result)) {
                    return Optional.ofNullable(result);
                }
                log.warn("engine {} returned an invalid result (attempt {}/{})", operation, attempt,
                        properties.maxAttempts());
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("engine {} timed out (attempt {}/{})", operation, attempt, properties.maxAttempts());
            } catch (ExecutionException e) {
                log.warn("engine {} failed with {} (attempt {}/{})", operation,
                        e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName(),
                        attempt, properties.maxAttempts());
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "engine-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
