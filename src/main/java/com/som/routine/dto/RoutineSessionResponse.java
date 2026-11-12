package com.som.routine.dto;

import com.som.routine.domain.RoutineSessionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RoutineSessionResponse(String routineId, LocalDate localDate, RoutineSessionStatus status,
                                     OffsetDateTime finishedAt) {}
