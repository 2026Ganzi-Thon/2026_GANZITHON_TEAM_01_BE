package com.som.connection.service;

import java.time.LocalDate;

/** 연동 데이터가 저장됐다(수신 트랜잭션 안에서 발행). 커밋 후 그날 스냅샷을 다시 계산하는 데 쓴다. */
public record SignalsIngestedEvent(Long userId, LocalDate localDate) {}
