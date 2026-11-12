package com.som.engine.routine;

import java.time.LocalDate;

public interface WoolTrimEngine {

    Boolean shouldPlay(String subjectKey, LocalDate localDate);
}
