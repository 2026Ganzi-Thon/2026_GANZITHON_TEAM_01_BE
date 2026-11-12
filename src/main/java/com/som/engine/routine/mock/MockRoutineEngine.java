package com.som.engine.routine.mock;

import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 개발·테스트용 추천 Mock. 카탈로그 순서를 기본으로, '잠깐 쉬어가기'면 숨 고르기를 맨 앞에 둔다(결정적). */
@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockRoutineEngine implements RoutineEngine {

    static final String FIRST_FOR_SHORT_BREAK = "BREATHING";

    @Override
    public RoutineRecommendation recommend(RoutineRequest request) {
        List<String> ids = new ArrayList<>(request.catalogIds());
        if ("SHORT_BREAK".equals(request.supportNeed()) && ids.remove(FIRST_FOR_SHORT_BREAK)) {
            ids.add(0, FIRST_FOR_SHORT_BREAK);
        }
        return new RoutineRecommendation(ids, "오늘의 작은 쉼을 골라 봤어요. (Mock 이유)");
    }
}
