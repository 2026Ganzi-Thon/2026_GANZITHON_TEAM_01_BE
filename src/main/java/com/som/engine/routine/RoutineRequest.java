package com.som.engine.routine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @param level             오늘 단계(스냅샷 값, 없으면 null)
 * @param supportNeed       오늘 체크인 Q3(없으면 null)
 * @param catalogIds        추천할 수 있는 루틴 ID(카탈로그 순서)
 * @param completedRecently 최근 완료한 루틴 ID(수행 이력)
 */
public record RoutineRequest(String subjectKey, LocalDate localDate, Integer level, String supportNeed,
                             List<String> catalogIds, List<String> completedRecently, String mood,
                             Map<String, Integer> catalogDurations) {
    public RoutineRequest(String subjectKey, LocalDate localDate, Integer level, String supportNeed,
                          List<String> catalogIds, List<String> completedRecently) {
        this(subjectKey, localDate, level, supportNeed, catalogIds, completedRecently, null, Map.of());
    }
}
