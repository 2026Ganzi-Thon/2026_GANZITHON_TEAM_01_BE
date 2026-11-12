package com.som.checkin.service;

import java.time.LocalDate;

/** 체크인이 저장(신규·갱신)됐다. 커밋 후 그날 스냅샷을 다시 계산하는 데 쓴다. 멱등 재생에서는 발행하지 않는다. */
public record CheckinSubmittedEvent(Long userId, LocalDate localDate) {}
