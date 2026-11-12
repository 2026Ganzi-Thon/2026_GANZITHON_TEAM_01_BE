package com.som.support;

import com.som.engine.insight.InsightEngine;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 요약 engine: fail(true)이면 예외, 받은 요청을 기록. */
public class SwitchableInsightEngine implements InsightEngine {

    private final InsightEngine delegate;
    private volatile boolean failing;
    private final List<InsightRequest> requests = new CopyOnWriteArrayList<>();

    public SwitchableInsightEngine(InsightEngine delegate) {
        this.delegate = delegate;
    }

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        failing = false;
        requests.clear();
    }

    public InsightRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    @Override
    public InsightResult summarize(InsightRequest request) {
        requests.add(request);
        if (failing) {
            throw new IllegalStateException("insight engine down (test)");
        }
        return delegate.summarize(request);
    }
}
