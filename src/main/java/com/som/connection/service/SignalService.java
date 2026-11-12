package com.som.connection.service;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.connection.SignalProperties;
import com.som.connection.domain.ConnectionProvider;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import com.som.connection.dto.SignalIngestRequest;
import com.som.connection.dto.SignalIngestRequest.Entry;
import com.som.connection.dto.SignalIngestResponse;
import com.som.connection.repository.DailySignalRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연동 데이터 수신(데모·개발용, provider=DEMO). 순서: 동의 확인(미동의면 403, 값을 읽거나 쓰지 않음)
 * → 요청 전체 검증(하나라도 틀리면 400, 부분 저장 없음) → 키별 upsert → 수면 분 파생.
 * 결측은 행을 만들지 않는다. 피로 계산·engine 호출은 하지 않는다. 값은 로그에 남기지 않는다.
 */
@Service
@EnableConfigurationProperties(SignalProperties.class)
public class SignalService {

    private final SourceConsentChecker consentChecker;
    private final DailySignalRepository signals;
    private final SignalProperties properties;
    private final ServiceDates dates;

    public SignalService(SourceConsentChecker consentChecker, DailySignalRepository signals,
                         SignalProperties properties, ServiceDates dates) {
        this.consentChecker = consentChecker;
        this.signals = signals;
        this.properties = properties;
        this.dates = dates;
    }

    @Transactional
    public SignalIngestResponse ingest(Long userId, SignalIngestRequest request) {
        if (!consentChecker.isConsented(userId, request.source())) {
            throw new ApiException(ErrorCode.SOURCE_NOT_CONSENTED);
        }
        validate(request);
        SleepTimes sleep = request.source() == DataSource.SLEEP ? sleepTimes(userId, request) : null;

        OffsetDateTime now = dates.now();
        for (Entry entry : request.entries()) {
            signals.upsert(userId, request.localDate(), request.source(), entry.metric(), entry.value(),
                    entry.valueAt(), entry.hourBucket(), ConnectionProvider.DEMO, now);
        }
        int stored = request.entries().size();
        if (sleep != null && sleep.complete()) {
            BigDecimal minutes = BigDecimal.valueOf(Duration.between(sleep.onset(), sleep.wake()).toMinutes());
            signals.upsert(userId, request.localDate(), DataSource.SLEEP, Metric.SLEEP_MINUTES, minutes, null, null,
                    ConnectionProvider.DEMO, now);
            stored++;
        }
        return new SignalIngestResponse(request.source(), request.localDate(), stored);
    }

    private void validate(SignalIngestRequest request) {
        if (request.localDate().isAfter(dates.today())) {
            throw invalid("localDate: 미래 날짜는 보낼 수 없습니다");
        }
        if (request.entries().size() > properties.maxEntriesPerRequest()) {
            throw invalid("entries: 한 번에 보낼 수 있는 개수를 넘었습니다");
        }
        Set<String> keys = new HashSet<>();
        for (Entry entry : request.entries()) {
            Metric metric = entry.metric();
            if (!metric.belongsTo(request.source())) {
                throw invalid("entries.metric: source에 속하지 않는 지표입니다");
            }
            if (!metric.clientWritable()) {
                throw invalid("entries.metric: 서버가 계산하는 지표입니다");
            }
            boolean time = metric.valueKind() == Metric.ValueKind.TIME;
            boolean kindOk = time
                    ? entry.valueAt() != null && entry.value() == null
                    : entry.value() != null && entry.valueAt() == null;
            if (!kindOk) {
                throw invalid(time ? "entries.valueAt: 이 지표는 valueAt만 보내 주세요"
                        : "entries.value: 이 지표는 value만 보내 주세요");
            }
            if (metric.hourly() != (entry.hourBucket() != null)) {
                throw invalid(metric.hourly() ? "entries.hourBucket: 값이 필요합니다"
                        : "entries.hourBucket: 이 지표에는 보낼 수 없습니다");
            }
            if (!keys.add(metric.name() + ":" + entry.hourBucket())) {
                throw invalid("entries: 같은 지표가 중복되었습니다");
            }
        }
    }

    /** 요청에 없으면 저장된 값으로 채운 잠든·깬 시각. 둘 다 있으면 깬 시각이 더 늦어야 한다(아니면 400, 저장 전). */
    private SleepTimes sleepTimes(Long userId, SignalIngestRequest request) {
        OffsetDateTime onset = valueAt(request.entries(), Metric.SLEEP_ONSET);
        OffsetDateTime wake = valueAt(request.entries(), Metric.SLEEP_WAKE);
        if (onset == null) {
            onset = signals.findValueAt(userId, request.localDate(), DataSource.SLEEP, Metric.SLEEP_ONSET).orElse(null);
        }
        if (wake == null) {
            wake = signals.findValueAt(userId, request.localDate(), DataSource.SLEEP, Metric.SLEEP_WAKE).orElse(null);
        }
        SleepTimes times = new SleepTimes(onset, wake);
        if (times.complete() && !wake.isAfter(onset)) {
            throw invalid("entries.valueAt: 깬 시각은 잠든 시각보다 늦어야 합니다");
        }
        return times;
    }

    private static OffsetDateTime valueAt(List<Entry> entries, Metric metric) {
        return entries.stream().filter(e -> e.metric() == metric).map(Entry::valueAt)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record SleepTimes(OffsetDateTime onset, OffsetDateTime wake) {
        boolean complete() {
            return onset != null && wake != null;
        }
    }
}
