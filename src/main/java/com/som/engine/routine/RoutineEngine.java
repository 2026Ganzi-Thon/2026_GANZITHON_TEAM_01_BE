package com.som.engine.routine;

/** 루틴 추천 계약(A_SPEC §12, B 합의 대기). 결과 routineId가 카탈로그에 있는지는 A가 확인한다. */
public interface RoutineEngine {
    RoutineRecommendation recommend(RoutineRequest request);
}
