package com.som.engine.fatigue;

public enum FatigueStatus {
    CALCULATED,
    /** 근거 데이터 없음: score·level·woolDisplayKg는 null이다(0점·1단계로 대체하지 않는다). */
    INSUFFICIENT_DATA
}
