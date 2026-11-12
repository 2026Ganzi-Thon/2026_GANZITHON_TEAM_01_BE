package com.som.engine.insight.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.insight.DailyState;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockInsightEngineTest {

    static final LocalDate MON = LocalDate.of(2026, 11, 9);

    final MockInsightEngine engine = new MockInsightEngine();

    private static DailyState day(int offset, Integer level, Integer score, String wool) {
        return new DailyState(MON.plusDays(offset), level, score, wool == null ? null : new BigDecimal(wool));
    }

    @Test
    void 기록이_없으면_평균_가장_무거운_날_털무게는_null이고_기록일은_0() {
        InsightResult result = engine.summarize(new InsightRequest("s", MON, MON.plusDays(6),
                List.of(day(0, null, null, null), day(1, null, null, null))));

        assertThat(result.averageLevel()).isNull();
        assertThat(result.recordedDays()).isZero();
        assertThat(result.heaviestDate()).isNull();
        assertThat(result.woolWeightKg()).isNull();
    }

    @Test
    void 평균은_기록일_단계의_산술평균_소수_첫째_자리_반올림() {
        InsightResult result = engine.summarize(new InsightRequest("s", MON, MON.plusDays(6), List.of(
                day(0, 2, 30, "1.6"), day(1, null, null, null), day(2, 3, 60, "2.4"), day(3, 3, 55, "2.4"))));

        assertThat(result.recordedDays()).isEqualTo(3);
        assertThat(result.averageLevel()).isEqualByComparingTo("2.7"); // 8/3 = 2.666…
        assertThat(result.heaviestDate()).isEqualTo(MON.plusDays(2)); // 같은 단계면 점수가 높은 날
        assertThat(result.woolWeightKg()).isEqualByComparingTo("2.4"); // 가장 최근 기록일
        assertThat(result.changeSummary()).isNotBlank();
    }

    @Test
    void 같은_입력이면_같은_결과() {
        InsightRequest request = new InsightRequest("s", MON, MON.plusDays(6), List.of(day(0, 2, 30, "1.6")));
        assertThat(engine.summarize(request)).isEqualTo(engine.summarize(request));
    }
}
