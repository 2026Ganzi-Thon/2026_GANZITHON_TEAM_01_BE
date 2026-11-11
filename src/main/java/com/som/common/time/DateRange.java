package com.som.common.time;

import java.time.LocalDate;
import java.util.List;

/** start~end 양 끝 포함 날짜 구간. */
public record DateRange(LocalDate start, LocalDate end) {

    public List<LocalDate> days() {
        return start.datesUntil(end.plusDays(1)).toList();
    }
}
