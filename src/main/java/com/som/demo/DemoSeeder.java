package com.som.demo;

import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.auth.service.SubjectKeyGenerator;
import com.som.checkin.domain.Checkin;
import com.som.checkin.domain.Mood;
import com.som.checkin.repository.CheckinRepository;
import com.som.common.time.ServiceDates;
import com.som.connection.domain.DataSource;
import com.som.connection.domain.Metric;
import com.som.connection.dto.SignalIngestRequest;
import com.som.connection.service.ConnectionService;
import com.som.connection.service.SignalService;
import com.som.consent.domain.RequiredConsent;
import com.som.consent.dto.ConsentChangeRequest;
import com.som.consent.service.ConsentService;
import com.som.state.service.SnapshotService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 시연용 데모 계정(A_SPEC §15 단계 4). demo 프로필에서만 실행되고 이미 있으면 건너뛴다(멱등).
 * 필수 동의 3개 → SLEEP·STEPS 연결 → 최근 7일 체크인·수면·걸음 수(provider DEMO, 일반 수신 경로) → 날짜별 계산.
 * 피로 점수는 engine이 계산한다(A가 값을 넣지 않는다). 비밀번호는 로그에 남기지 않는다.
 */
@Component
@Profile("demo")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final DemoProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SubjectKeyGenerator subjectKeys;
    private final ConsentService consents;
    private final ConnectionService connections;
    private final SignalService signals;
    private final CheckinRepository checkins;
    private final SnapshotService snapshots;
    private final ServiceDates dates;

    public DemoSeeder(DemoProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
                      SubjectKeyGenerator subjectKeys, ConsentService consents, ConnectionService connections,
                      SignalService signals, CheckinRepository checkins, SnapshotService snapshots,
                      ServiceDates dates) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.subjectKeys = subjectKeys;
        this.consents = consents;
        this.connections = connections;
        this.signals = signals;
        this.checkins = checkins;
        this.snapshots = snapshots;
        this.dates = dates;
    }

    public DemoProperties properties() {
        return properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(properties);
    }

    public void seed(DemoProperties props) {
        if (props.password() == null || props.password().isBlank()) {
            log.warn("demo seed skipped: SOM_DEMO_PASSWORD is not set");
            return;
        }
        if (users.existsByLoginId(props.loginId())) {
            log.info("demo seed skipped: account already exists");
            return;
        }
        OffsetDateTime now = dates.now();
        Long userId = users.saveAndFlush(User.register(props.loginId(), passwordEncoder.encode(props.password()),
                subjectKeys.next(), now)).getId();
        consents.change(userId, new ConsentChangeRequest(Arrays.stream(RequiredConsent.values())
                .map(key -> new ConsentChangeRequest.Item(key, true)).toList()));
        connections.connect(userId, DataSource.SLEEP);
        connections.connect(userId, DataSource.STEPS);

        LocalDate today = dates.today();
        for (DemoProperties.Day day : props.days()) {
            LocalDate date = today.minusDays(day.offset());
            checkins.saveAndFlush(Checkin.create(userId, date, day.mood() == null ? null : Mood.valueOf(day.mood()),
                    List.of(), null, now));
            ingestSleep(userId, date, day);
            if (day.steps() != null) {
                signals.ingest(userId, new SignalIngestRequest(DataSource.STEPS, date, List.of(
                        new SignalIngestRequest.Entry(Metric.STEPS_DAILY, BigDecimal.valueOf(day.steps()), null, null))));
            }
            snapshots.recalculate(userId, date);
        }
        log.info("demo seed created {} days", props.days().size());
    }

    private void ingestSleep(Long userId, LocalDate date, DemoProperties.Day day) {
        if (day.sleepOnset() == null || day.sleepWake() == null) {
            return;
        }
        LocalTime onset = LocalTime.parse(day.sleepOnset());
        LocalTime wake = LocalTime.parse(day.sleepWake());
        LocalDate onsetDate = onset.isAfter(wake) ? date.minusDays(1) : date; // 23:30 잠 → 전날 밤
        List<SignalIngestRequest.Entry> entries = new ArrayList<>();
        entries.add(new SignalIngestRequest.Entry(Metric.SLEEP_ONSET, null,
                onsetDate.atTime(onset).atZone(dates.now().getOffset()).toOffsetDateTime(), null));
        entries.add(new SignalIngestRequest.Entry(Metric.SLEEP_WAKE, null,
                date.atTime(wake).atZone(dates.now().getOffset()).toOffsetDateTime(), null));
        signals.ingest(userId, new SignalIngestRequest(DataSource.SLEEP, date, entries));
    }
}
