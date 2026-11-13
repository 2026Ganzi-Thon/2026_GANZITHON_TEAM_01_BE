package com.som.engine.fatigue;

import java.time.LocalDate;
import java.util.List;

public record SignalDay(LocalDate localDate, List<SignalInput> signals) {}
