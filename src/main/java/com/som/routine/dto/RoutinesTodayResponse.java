package com.som.routine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * 루틴-1 화면(A_SPEC §9). routines 순서는 RoutineEngine 추천 결과이고, isStale이면 이전 날 추천이다.
 *
 * @param level 오늘 피로 단계(스냅샷 값, 없으면 null)
 * @param week  이번 주 월~일 이행 표시. done은 미래면 null
 */
public record RoutinesTodayResponse(LocalDate localDate, Integer level, List<DayDone> week,
                                    List<RoutineItem> routines, boolean journalUnlocked,
                                    @JsonProperty("isStale") boolean isStale) {

    public record DayDone(LocalDate localDate, Boolean done) {}

    /** @param status NOT_STARTED · STARTED · COMPLETED · SKIPPED */
    public record RoutineItem(String routineId, String title, String subtitle, String instruction, int durationSec,
                              String assetKey, String status) {}
}
