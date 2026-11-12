package com.som.engine.fatigue.mock;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock 엔진 상수(som.engine.mock). B 초안(A_SPEC §12)이며 확정값이 아니다.
 *
 * @param levelMinScores 2·3·4단계가 시작되는 점수(3개)
 * @param woolKg         1~4단계 털 무게(4개)
 * @param levels         1~4단계 상태·보조 문구(4개, A_SPEC §7 표)
 */
@ConfigurationProperties(prefix = "som.engine.mock")
public record MockEngineProperties(List<Integer> levelMinScores, List<BigDecimal> woolKg, List<LevelText> levels) {

    public record LevelText(String statusText, String subText) {}

    public MockEngineProperties {
        if (levelMinScores == null || levelMinScores.size() != 3 || woolKg == null || woolKg.size() != 4
                || levels == null || levels.size() != 4) {
            throw new IllegalStateException("som.engine.mock needs 3 level-min-scores, 4 wool-kg and 4 levels");
        }
    }
}
