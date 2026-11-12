package com.som.engine.fatigue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 동의한 연동 소스의 일 단위 값 하나. value(수치)와 valueAt(시각) 중 지표에 맞는 하나만 있다. */
public record SignalInput(String source, String metric, BigDecimal value, OffsetDateTime valueAt, String unit,
                          Integer hourBucket) {

    @Override
    public String toString() {
        return "SignalInput[source=" + source + ", metric=" + metric + "]";
    }
}
