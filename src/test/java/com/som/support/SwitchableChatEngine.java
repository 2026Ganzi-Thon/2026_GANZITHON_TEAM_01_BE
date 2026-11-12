package com.som.support;

import com.som.engine.chat.ChatEngine;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** 테스트용 대화 engine: fail(true)이면 예외, override로 결과 교체, 받은 요청을 기록한다. */
public class SwitchableChatEngine implements ChatEngine {

    private final ChatEngine delegate;
    private volatile boolean failing;
    private volatile Function<ChatRequest, ChatResult> override;
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

    public SwitchableChatEngine(ChatEngine delegate) {
        this.delegate = delegate;
    }

    public void fail(boolean failing) {
        this.failing = failing;
    }

    public void override(Function<ChatRequest, ChatResult> override) {
        this.override = override;
    }

    public void reset() {
        failing = false;
        override = null;
        requests.clear();
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    public ChatRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    @Override
    public ChatResult respond(ChatRequest request) {
        requests.add(request);
        if (failing) {
            throw new IllegalStateException("chat engine down (test)");
        }
        Function<ChatRequest, ChatResult> o = override;
        return o != null ? o.apply(request) : delegate.respond(request);
    }
}
