package com.som.connection.dto;

import com.som.connection.domain.DataSource;
import java.time.LocalDate;

/** stored: 이 요청으로 저장(갱신 포함)된 행 수. 서버 파생 행(SLEEP_MINUTES)을 포함한다. */
public record SignalIngestResponse(DataSource source, LocalDate localDate, int stored) {}
