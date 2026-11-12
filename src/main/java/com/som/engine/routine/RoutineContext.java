package com.som.engine.routine;

import java.util.List;
import java.util.Map;

public record RoutineContext(Integer level, List<String> catalogIds, List<String> completedRecently,
                             Map<String, Integer> catalogDurations) {}
