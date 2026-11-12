package com.som.support;

import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 engine: fail(true)이면 예외를 던지고, 받은 요청을 모두 기록한다. reset()으로 초기화. */
public class SwitchableFatigueEngine implements FatigueEngine {

    private final FatigueEngine delegate;
    private volatile boolean failing;
    private final List<FatigueRequest> requests = new CopyOnWriteArrayList<>();

    public SwitchableFatigueEngine(FatigueEngine delegate) {
        this.delegate = delegate;
    }

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        failing = false;
        requests.clear();
    }

    public List<FatigueRequest> requests() {
        return List.copyOf(requests);
    }

    public FatigueRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    @Override
    public FatigueResult calculate(FatigueRequest request) {
        requests.add(request);
        if (failing) {
            throw new IllegalStateException("engine down (test)");
        }
        return delegate.calculate(request);
    }
}
