package com.som.engine.routine.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockRoutineEngineTest {

    static final List<String> CATALOG = List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");

    final MockRoutineEngine engine = new MockRoutineEngine();

    private RoutineRequest request(Integer level, String supportNeed) {
        return new RoutineRequest("s", LocalDate.of(2026, 11, 11), level, supportNeed, CATALOG, List.of());
    }

    @Test
    void 카탈로그_전체를_한_번씩_돌려주고_결정적이다() {
        RoutineRecommendation result = engine.recommend(request(2, "COMFORT"));

        assertThat(result.routineIds()).containsExactlyInAnyOrderElementsOf(CATALOG);
        assertThat(engine.recommend(request(2, "COMFORT"))).isEqualTo(result);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void 잠깐_쉬어가기를_원하면_숨_고르기가_먼저() {
        assertThat(engine.recommend(request(3, "SHORT_BREAK")).routineIds().get(0)).isEqualTo("BREATHING");
    }

    @Test
    void 정보가_없어도_카탈로그_순서로_돌려준다() {
        assertThat(engine.recommend(request(null, null)).routineIds()).containsExactlyElementsOf(CATALOG);
    }
}
