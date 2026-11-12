package com.som.connection.dto;

import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * POST /api/v1/signals 바디. 정의된 필드 외('title'·'location'·'appName'·'userId' 등)는 400(엄격 JSON).
 * toString은 값을 노출하지 않는다(연동 원값 로그 금지).
 */
public record SignalIngestRequest(@NotNull DataSource source, @NotNull LocalDate localDate,
                                  @NotEmpty List<@NotNull @Valid Entry> entries) {

    /** value(수치) 또는 valueAt(시각) 중 지표에 맞는 하나만. hourBucket은 시간대별 지표만(0~23). */
    public record Entry(@NotNull Metric metric,
                        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal value,
                        OffsetDateTime valueAt,
                        @Min(0) @Max(23) Integer hourBucket) {
        @Override
        public String toString() {
            return "Entry[metric=" + metric + "]";
        }
    }

    @Override
    public String toString() {
        return "SignalIngestRequest[source=" + source + ", entries=" + (entries == null ? 0 : entries.size()) + "]";
    }
}
