package com.som.engine.insight.mock;

import com.som.engine.insight.DailyState;
import com.som.engine.insight.InsightEngine;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발·테스트용 요약 Mock(A_SPEC §7 평균 규칙). 실제 요약·문구는 B 몫이다.
 * 평균 = 단계가 있는 날의 산술평균(소수 첫째 자리, HALF_UP). 가장 무거운 날 = 단계가 가장 높은 날(같으면 점수, 그다음 이른 날).
 * 털 무게(주간)는 가장 최근 기록일의 값을 쓴다 — Mock 규칙이며 B 합의 대기.
 */
@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockInsightEngine implements InsightEngine {

    static final String CHANGE_SUMMARY = "이번 기간의 마음 기록을 모아 봤어요. (Mock 요약)";
    static final String HEAVIEST_REASON = "가장 높은 단계를 기록한 날이에요. (Mock 근거)";

    @Override
    public InsightResult summarize(InsightRequest request) {
        List<DailyState> recorded = request.days().stream().filter(d -> d.level() != null).toList();
        if (recorded.isEmpty()) {
            return new InsightResult(null, 0, null, null, null, null);
        }
        BigDecimal average = BigDecimal.valueOf(recorded.stream().mapToInt(DailyState::level).sum())
                .divide(BigDecimal.valueOf(recorded.size()), 1, RoundingMode.HALF_UP);
        DailyState heaviest = recorded.stream()
                .max(Comparator.comparing(DailyState::level)
                        .thenComparing(d -> d.score() == null ? -1 : d.score())
                        .thenComparing(DailyState::localDate, Comparator.reverseOrder()))
                .orElseThrow();
        BigDecimal wool = recorded.get(recorded.size() - 1).woolDisplayKg();
        return new InsightResult(average, recorded.size(), CHANGE_SUMMARY, heaviest.localDate(), HEAVIEST_REASON, wool);
    }
}
