package com.som.support;

import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 engine: fail(true)이면 예외를 던지고, 받은 요청을 모두 기록한다. reset()으로 초기화. */
public class SwitchableFatigueEngine implements FatigueEngine {

    private final FatigueEngine delegate;
    private volatile boolean failing;
    private final List<FatigueRequest> requests = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    /** 다음 한 번의 호출을 release가 열릴 때까지 멈춘다(호출이 들어오면 entered가 열린다). */
    public void pauseNext(CountDownLatch entered, CountDownLatch release) {
        this.entered = entered;
        this.release = release;
    }

    public SwitchableFatigueEngine(FatigueEngine delegate) {
        this.delegate = delegate;
    }

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        failing = false;
        requests.clear();
        entered = null;
        release = null;
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
        CountDownLatch gate = release;
        if (gate != null) {
            CountDownLatch in = entered;
            release = null;
            entered = null;
            in.countDown();
            try {
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failing) {
            throw new IllegalStateException("engine down (test)");
        }
        return delegate.calculate(request);
    }
}
