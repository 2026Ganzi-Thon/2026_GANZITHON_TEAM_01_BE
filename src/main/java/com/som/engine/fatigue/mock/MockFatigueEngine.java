package com.som.engine.fatigue.mock;

import com.som.engine.fatigue.CheckinInput;
import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.FatigueStatus;
import com.som.engine.fatigue.SignalInput;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A가 작성하는 개발·테스트용 Mock(A_SPEC §12 Mock 규칙). 실제 공식은 B 몫이다.
 * 체크인·신호가 모두 없으면 INSUFFICIENT_DATA, 있으면 입력만으로 정해지는 결정적 계산(난수·현재 시각 미사용).
 * 아래 가중치는 Mock 임의값이며 의미 있는 피로 모델이 아니다.
 */
@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "mock", matchIfMissing = true)
@EnableConfigurationProperties(MockEngineProperties.class)
public class MockFatigueEngine implements FatigueEngine {

    static final String ALGORITHM_VERSION = "mock-0.1";

    private static final Map<String, Integer> MOOD_POINTS = Map.of(
            "CALM", 0, "OKAY", 10, "SO_SO", 20, "OTHER", 20, "ANXIOUS", 35, "TIRED", 40);
    private static final int STRESSOR_POINTS = 10;
    private static final int MAX_STRESSOR_POINTS = 20;

    private final MockEngineProperties props;
    private final Clock clock;

    public MockFatigueEngine(MockEngineProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public FatigueResult calculate(FatigueRequest request) {
        OffsetDateTime calculatedAt = OffsetDateTime.now(clock);
        CheckinInput checkin = request.checkin() == null || request.checkin().isEmpty() ? null : request.checkin();
        List<SignalInput> signals = request.signals() == null ? List.of() : request.signals();
        if (checkin == null && signals.isEmpty()) {
            return new FatigueResult(null, null, null, FatigueStatus.INSUFFICIENT_DATA, null, null, null, List.of(),
                    ALGORITHM_VERSION, calculatedAt);
        }

        Set<String> used = new LinkedHashSet<>();
        int score = 0;
        if (checkin != null) {
            used.add("CHECKIN");
            score += checkinPoints(checkin);
        }
        int screenMinutes = 0;
        for (SignalInput signal : signals) {
            used.add(signal.source());
            BigDecimal value = signal.value();
            if (value == null) {
                continue;
            }
            switch (signal.metric()) {
                case "SLEEP_MINUTES" -> score += value.intValue() < 360 ? 20 : value.intValue() < 420 ? 10 : 0;
                case "STEPS_DAILY" -> score += value.intValue() < 3000 ? 10 : 0;
                case "SCREEN_TIME_MINUTES" -> screenMinutes += value.intValue();
                case "CALENDAR_BUSY_MINUTES" -> score += value.intValue() > 300 ? 10 : 0;
                default -> { }
            }
        }
        if (screenMinutes > 360) {
            score += 10;
        }
        score = Math.min(score, 100);

        int level = levelOf(score);
        MockEngineProperties.LevelText text = props.levels().get(level - 1);
        return new FatigueResult(score, level, props.woolKg().get(level - 1), FatigueStatus.CALCULATED,
                text.statusText(), text.subText(), "sheep_level_" + level, new ArrayList<>(used),
                ALGORITHM_VERSION, calculatedAt);
    }

    /** 점수→단계(B 초안: 0~24=1, 25~49=2, 50~74=3, 75~100=4). 경계는 설정값. */
    int levelOf(int score) {
        int level = 1;
        for (int min : props.levelMinScores()) {
            if (score >= min) {
                level++;
            }
        }
        return level;
    }

    private static int checkinPoints(CheckinInput checkin) {
        int points = checkin.mood() == null ? 0 : MOOD_POINTS.getOrDefault(checkin.mood(), 0);
        long stressors = checkin.stressors() == null ? 0
                : checkin.stressors().stream().filter(s -> !"NO_REASON".equals(s)).count();
        return points + (int) Math.min(stressors * STRESSOR_POINTS, MAX_STRESSOR_POINTS);
    }
}
