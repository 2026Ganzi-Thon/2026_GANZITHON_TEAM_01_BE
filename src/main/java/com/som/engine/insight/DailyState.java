package com.som.engine.insight;

import com.som.engine.fatigue.SignalChange;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 하루 상태(스냅샷 값 그대로). 미기록·INSUFFICIENT_DATA인 날은 level·score·woolDisplayKg가 null. */
public record DailyState(LocalDate localDate, Integer level, Integer score, BigDecimal woolDisplayKg,
                         List<SignalChange> changes) {
    public DailyState(LocalDate localDate, Integer level, Integer score, BigDecimal woolDisplayKg) {
        this(localDate, level, score, woolDisplayKg, List.of());
    }
}
