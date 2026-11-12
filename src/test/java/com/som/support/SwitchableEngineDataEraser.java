package com.som.support;

import com.som.engine.data.EngineDataEraser;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용: fail(true)이면 예외, 호출된 subjectKey를 기록한다. */
public class SwitchableEngineDataEraser implements EngineDataEraser {

    private volatile boolean failing;
    private final List<String> erased = new CopyOnWriteArrayList<>();

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        failing = false;
        erased.clear();
    }

    public List<String> erased() {
        return List.copyOf(erased);
    }

    @Override
    public void eraseSubject(String subjectKey) {
        erased.add(subjectKey);
        if (failing) {
            throw new IllegalStateException("engine erase down (test)");
        }
    }
}
