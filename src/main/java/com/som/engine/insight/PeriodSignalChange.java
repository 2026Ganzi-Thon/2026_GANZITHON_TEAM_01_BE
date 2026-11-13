package com.som.engine.insight;

public record PeriodSignalChange(String source, String metric, int deltaMinutes, int changedDays) {}
