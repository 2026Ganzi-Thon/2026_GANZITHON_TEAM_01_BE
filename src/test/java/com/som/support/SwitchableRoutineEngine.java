package com.som.support;

import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** 테스트용 추천 engine: fail(true)이면 예외, override로 결과를 바꿀 수 있고, 받은 요청을 기록한다. */
public class SwitchableRoutineEngine implements RoutineEngine {

    private final RoutineEngine delegate;
    private volatile boolean failing;
    private volatile Function<RoutineRequest, RoutineRecommendation> override;
    private final List<RoutineRequest> requests = new CopyOnWriteArrayList<>();

    public SwitchableRoutineEngine(RoutineEngine delegate) {
        this.delegate = delegate;
    }

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void override(Function<RoutineRequest, RoutineRecommendation> override) {
        this.override = override;
    }

    public void reset() {
        failing = false;
        override = null;
        requests.clear();
    }

    public List<RoutineRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public RoutineRecommendation recommend(RoutineRequest request) {
        requests.add(request);
        if (failing) {
            throw new IllegalStateException("routine engine down (test)");
        }
        Function<RoutineRequest, RoutineRecommendation> o = override;
        return o != null ? o.apply(request) : delegate.recommend(request);
    }
}
