package com.som.state.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.FatigueStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EngineGatewayTest {

    final EngineGateway gateway = new EngineGateway(new EngineProperties(Duration.ofMillis(200), 2, 2));
    final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void tearDown() {
        gateway.shutdown();
    }

    static FatigueResult calculated(Integer score, Integer level) {
        return new FatigueResult(score, level, new BigDecimal("1.6"), FatigueStatus.CALCULATED, "t", "s", "a",
                List.of("CHECKIN"), "v", OffsetDateTime.parse("2026-11-11T14:00:00+09:00"));
    }

    @Test
    void 정상이면_결과를_돌려주고_한_번만_부른다() {
        Optional<String> result = gateway.call("op", () -> { calls.incrementAndGet(); return "ok"; }, r -> true);

        assertThat(result).contains("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void 첫_시도가_실패하고_두_번째가_성공하면_결과를_돌려준다() {
        Optional<String> result = gateway.call("op", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first");
            }
            return "ok";
        }, r -> true);

        assertThat(result).contains("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void 계속_실패하면_empty이고_max_attempts만큼만_부른다(CapturedOutput output) {
        Optional<String> result = gateway.call("fatigue.calculate", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("secret-payload-123");
        }, r -> true);

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(2);
        assertThat(output.getOut() + output.getErr()).contains("fatigue.calculate").doesNotContain("secret-payload-123");
    }

    @Test
    void 타임아웃이면_empty() {
        Optional<String> result = gateway.call("op", () -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        }, r -> true);

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    void 검증에_실패한_결과는_empty이고_로그에_결과_내용이_없다(CapturedOutput output) {
        Optional<FatigueResult> result = gateway.call("op", () -> calculated(48, 5), FatigueResultValidator::isValid);

        assertThat(result).isEmpty();
        assertThat(output.getOut()).contains("invalid result").doesNotContain("FatigueResult[").doesNotContain("score=");
    }

    @Test
    void 피로_결과_검증_규칙() {
        assertThat(FatigueResultValidator.isValid(calculated(48, 2))).isTrue();
        assertThat(FatigueResultValidator.isValid(calculated(48, 5))).isFalse();
        assertThat(FatigueResultValidator.isValid(calculated(null, 2))).isFalse();
        assertThat(FatigueResultValidator.isValid(calculated(101, 4))).isFalse();
        assertThat(FatigueResultValidator.isValid(new FatigueResult(48, 2, BigDecimal.ZERO, FatigueStatus.CALCULATED,
                "t", "s", "a", List.of(), "v", OffsetDateTime.now()))).isFalse();
        assertThat(FatigueResultValidator.isValid(new FatigueResult(48, 2, new BigDecimal("1.6"),
                FatigueStatus.CALCULATED, "t", "s", "a", List.of(), "v", null))).isFalse();

        assertThat(FatigueResultValidator.isValid(new FatigueResult(null, null, null, FatigueStatus.INSUFFICIENT_DATA,
                null, null, null, List.of(), "v", OffsetDateTime.now()))).isTrue();
        assertThat(FatigueResultValidator.isValid(new FatigueResult(null, 1, null, FatigueStatus.INSUFFICIENT_DATA,
                null, null, null, List.of(), "v", OffsetDateTime.now()))).isFalse();
        assertThat(FatigueResultValidator.isValid(new FatigueResult(0, null, null, FatigueStatus.INSUFFICIENT_DATA,
                null, null, null, List.of(), "v", OffsetDateTime.now()))).isFalse();
        assertThat(FatigueResultValidator.isValid(null)).isFalse();
    }
}
