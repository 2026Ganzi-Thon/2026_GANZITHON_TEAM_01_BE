package com.som.state.service;

import java.time.LocalDate;

/**
 * 그날 털 깎임 연출이 있었는지(마음 정리 완료 등). 연출 값일 뿐 score·level을 바꾸지 않는다(CRITICAL).
 * 구현: journal.service.JournalWoolTrimQuery(wool_trim_events).
 */
public interface WoolTrimQuery {
    boolean trimmedOn(Long userId, LocalDate date);
}
