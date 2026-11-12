package com.som.engine.insight;

/**
 * 기간 요약 계약(A_SPEC §12, B 합의 대기). 평균·기록일·가장 무거운 날은 engine이 정한다(A에서 계산하지 않는다).
 * A는 EngineGateway를 거쳐 호출한다.
 */
public interface InsightEngine {
    InsightResult summarize(InsightRequest request);
}
