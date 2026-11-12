package com.som.engine.fatigue;

/**
 * 피로 계산 계약(A_SPEC §12, B 합의 대기). 실제 구현은 B가 작성하고, A는 Mock만 둔다(ADR-002).
 * A는 이 인터페이스를 직접 부르지 않고 타임아웃·재시도 상한이 있는 EngineGateway를 거친다.
 */
public interface FatigueEngine {
    FatigueResult calculate(FatigueRequest request);
}
