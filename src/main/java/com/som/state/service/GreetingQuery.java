package com.som.state.service;

import java.time.LocalDate;
import java.util.Optional;

/** 그날 저장된 선제 인사 문구(홈 말풍선용). 구현: chat.service.ChatGreetingService. 조회만 하고 인사를 만들지 않는다. */
public interface GreetingQuery {
    Optional<String> greetingOn(Long userId, LocalDate date);
}
