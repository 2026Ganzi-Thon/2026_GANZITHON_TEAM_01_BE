package com.som.routine.service;

import java.time.LocalDate;

public record RoutineCompletedEvent(Long userId, LocalDate localDate) {}
