package com.som.engine.fatigue.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.fatigue.CheckinInput;
import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.FatigueStatus;
import com.som.engine.fatigue.SignalInput;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MockFatigueEngineTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T14:00:00Z"), SEOUL);
    static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    static final MockEngineProperties PROPS = new MockEngineProperties(
            List.of(25, 50, 75),
            List.of(new BigDecimal("0.8"), new BigDecimal("1.6"), new BigDecimal("2.4"), new BigDecimal("3.2")),
            List.of(new MockEngineProperties.LevelText("마음이 가벼워요", "지금의 편안함을 느껴보세요."),
                    new MockEngineProperties.LevelText("조금 지쳐 있어요", "잠깐 쉬어가도 괜찮아요."),
                    new MockEngineProperties.LevelText("피로가 쌓였어요", "오늘은 나를 먼저 돌봐주세요."),
                    new MockEngineProperties.LevelText("충분한 쉼이 필요해요", "혼자 애쓰지 않아도 괜찮아요.")));

    final MockFatigueEngine engine = new MockFatigueEngine(PROPS, CLOCK);

    private FatigueRequest request(CheckinInput checkin, List<SignalInput> signals) {
        return new FatigueRequest("subject-1", DAY, SEOUL, checkin, signals, Set.of("SLEEP"));
    }

    private static SignalInput sleepMinutes(int minutes) {
        return new SignalInput("SLEEP", "SLEEP_MINUTES", BigDecimal.valueOf(minutes), null, "min", null);
    }

    @Test
    void 체크인과_신호가_모두_없으면_INSUFFICIENT_DATA이고_점수_단계_털무게는_null() {
        FatigueResult result = engine.calculate(request(null, List.of()));

        assertThat(result.status()).isEqualTo(FatigueStatus.INSUFFICIENT_DATA);
        assertThat(result.score()).isNull();
        assertThat(result.level()).isNull();
        assertThat(result.woolDisplayKg()).isNull();
        assertThat(result.usedSources()).isEmpty();
        assertThat(result.calculatedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
    }

    @Test
    void 체크인_세_값이_모두_null이고_신호가_없으면_INSUFFICIENT_DATA() {
        FatigueResult result = engine.calculate(request(new CheckinInput(null, List.of(), null), List.of()));

        assertThat(result.status()).isEqualTo(FatigueStatus.INSUFFICIENT_DATA);
        assertThat(result.level()).isNull();
    }

    @Test
    void 같은_입력이면_같은_결과다() {
        FatigueRequest req = request(new CheckinInput("TIRED", List.of("WORK"), "COMFORT"), List.of(sleepMinutes(300)));

        assertThat(engine.calculate(req)).isEqualTo(engine.calculate(req));
    }

    @Test
    void 체크인이_있으면_CALCULATED이고_단계에_맞는_털무게와_문구() {
        FatigueResult result = engine.calculate(request(new CheckinInput("CALM", List.of(), null), List.of()));

        assertThat(result.status()).isEqualTo(FatigueStatus.CALCULATED);
        assertThat(result.score()).isBetween(0, 100);
        int level = result.level();
        assertThat(result.woolDisplayKg()).isEqualByComparingTo(PROPS.woolKg().get(level - 1));
        assertThat(result.statusText()).isEqualTo(PROPS.levels().get(level - 1).statusText());
        assertThat(result.subText()).isEqualTo(PROPS.levels().get(level - 1).subText());
        assertThat(result.assetKey()).isEqualTo("sheep_level_" + level);
        assertThat(result.usedSources()).containsExactly("CHECKIN");
        assertThat(result.algorithmVersion()).isEqualTo("mock-0.1");
    }

    @Test
    void 신호만_있어도_CALCULATED이고_usedSources에는_신호_소스만() {
        FatigueResult result = engine.calculate(request(null, List.of(sleepMinutes(300))));

        assertThat(result.status()).isEqualTo(FatigueStatus.CALCULATED);
        assertThat(result.usedSources()).containsExactly("SLEEP");
    }

    @Test
    void 피로_신호가_많을수록_단계가_높거나_같다() {
        FatigueResult light = engine.calculate(request(new CheckinInput("CALM", List.of("NO_REASON"), null), List.of()));
        FatigueResult heavy = engine.calculate(request(new CheckinInput("TIRED", List.of("WORK"), null),
                List.of(sleepMinutes(240))));

        assertThat(heavy.score()).isGreaterThan(light.score());
        assertThat(heavy.level()).isGreaterThanOrEqualTo(light.level());
    }

    @Test
    void 점수_경계에서_단계가_바뀐다() {
        assertThat(List.of(0, 24, 25, 49, 50, 74, 75, 100).stream().map(engine::levelOf).toList())
                .containsExactly(1, 1, 2, 2, 3, 3, 4, 4);
    }

    @Configuration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class, MockFatigueEngine.class)
            .withPropertyValues("som.engine.mock.level-min-scores=25,50,75",
                    "som.engine.mock.wool-kg=0.8,1.6,2.4,3.2",
                    "som.engine.mock.levels[0].status-text=a", "som.engine.mock.levels[0].sub-text=a",
                    "som.engine.mock.levels[1].status-text=b", "som.engine.mock.levels[1].sub-text=b",
                    "som.engine.mock.levels[2].status-text=c", "som.engine.mock.levels[2].sub-text=c",
                    "som.engine.mock.levels[3].status-text=d", "som.engine.mock.levels[3].sub-text=d");

    @Test
    void mode가_mock이거나_없으면_Mock_빈이_등록되고_real이면_없다() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(FatigueEngine.class));
        runner.withPropertyValues("som.engine.mode=mock").run(ctx -> assertThat(ctx).hasSingleBean(FatigueEngine.class));
        runner.withPropertyValues("som.engine.mode=real").run(ctx -> assertThat(ctx).doesNotHaveBean(FatigueEngine.class));
    }
}
