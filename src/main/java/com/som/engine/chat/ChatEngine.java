package com.som.engine.chat;

/** 대화 계약(A_SPEC §8·§12, B 합의 대기). A는 EngineGateway를 거쳐 호출하고, 실패하면 고정 문구를 쓴다. */
public interface ChatEngine {
    ChatResult respond(ChatRequest request);
}
