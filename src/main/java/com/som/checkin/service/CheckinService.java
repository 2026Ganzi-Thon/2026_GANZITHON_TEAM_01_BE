package com.som.checkin.service;

import com.som.checkin.CheckinProperties;
import com.som.checkin.domain.Checkin;
import com.som.checkin.domain.Stressor;
import com.som.checkin.dto.CheckinRequest;
import com.som.checkin.dto.CheckinResponse;
import com.som.checkin.repository.CheckinRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.idempotency.IdempotencyService;
import com.som.common.idempotency.IdempotentResult;
import com.som.common.lock.AdvisoryLock;
import com.som.common.time.ServiceDates;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 체크인 제출·조회(A_SPEC §6). 날짜는 서버가 KST로 정하고 과거 날짜 제출 경로는 없다.
 * 피로 점수·단계는 여기서 만들지 않는다(engine 결과만). 저장하면 CheckinSubmittedEvent를 발행하고,
 * state 패키지가 커밋 후 SnapshotService로 다시 계산한다.
 */
@Service
@EnableConfigurationProperties(CheckinProperties.class)
public class CheckinService {

    static final String SUBMIT_ENDPOINT = "POST /api/v1/checkins";

    private final CheckinRepository checkins;
    private final IdempotencyService idempotency;
    private final AdvisoryLock locks;
    private final ServiceDates dates;
    private final CheckinProperties properties;
    private final ApplicationEventPublisher events;

    public CheckinService(CheckinRepository checkins, IdempotencyService idempotency, AdvisoryLock locks,
                          ServiceDates dates, CheckinProperties properties, ApplicationEventPublisher events) {
        this.checkins = checkins;
        this.idempotency = idempotency;
        this.locks = locks;
        this.dates = dates;
        this.properties = properties;
        this.events = events;
    }

    /** 처음 제출 201, 같은 날 재제출 200(최신으로 갱신). Idempotency-Key 재생이면 처음 응답 그대로. */
    // TODO(DECISION): 재제출 정책(A_SPEC §6) — 기본: 최신으로 갱신. 저장 시점도 기본(마지막 문항에서 1회 제출)을 따른다
    @Transactional
    public IdempotentResult<CheckinResponse> submit(Long userId, CheckinRequest request, String idempotencyKey) {
        CheckinRequest body = request.normalized();
        validate(body);
        return idempotency.execute(userId, SUBMIT_ENDPOINT, idempotencyKey, body, CheckinResponse.class,
                () -> upsert(userId, body));
    }

    private IdempotentResult.Outcome<CheckinResponse> upsert(Long userId, CheckinRequest body) {
        LocalDate today = dates.today();
        OffsetDateTime now = dates.now();
        locks.lock("checkin:" + userId + ":" + today); // 같은 날 동시 첫 제출이 유니크 위반(500)이 되지 않게
        events.publishEvent(new CheckinSubmittedEvent(userId, today)); // 커밋 후 스냅샷 재계산(state)
        Optional<Checkin> existing = checkins.findByUserIdAndLocalDate(userId, today);
        if (existing.isPresent()) {
            Checkin checkin = existing.get();
            checkin.update(body.mood(), body.stressors(), body.supportNeed(), now);
            return new IdempotentResult.Outcome<>(HttpStatus.OK.value(), toResponse(checkins.saveAndFlush(checkin)));
        }
        Checkin created = checkins.saveAndFlush(
                Checkin.create(userId, today, body.mood(), body.stressors(), body.supportNeed(), now));
        return new IdempotentResult.Outcome<>(HttpStatus.CREATED.value(), toResponse(created));
    }

    @Transactional(readOnly = true)
    public CheckinResponse get(Long userId, LocalDate date) {
        return checkins.findByUserIdAndLocalDate(userId, date).map(this::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CheckinResponse> list(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw invalid("from: to보다 늦을 수 없습니다");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > properties.maxRangeDays()) {
            throw invalid("to: 조회 기간은 최대 " + properties.maxRangeDays() + "일입니다");
        }
        return checkins.findByUserIdAndLocalDateBetweenOrderByLocalDate(userId, from, to).stream()
                .map(this::toResponse).toList();
    }

    private void validate(CheckinRequest body) {
        List<Stressor> stressors = body.stressors();
        if (stressors.stream().anyMatch(Objects::isNull)) {
            throw invalid("stressors: 빈 값은 보낼 수 없습니다");
        }
        if (new HashSet<>(stressors).size() != stressors.size()) {
            throw invalid("stressors: 같은 항목이 중복되었습니다");
        }
        if (stressors.size() > properties.maxStressors()) {
            throw invalid("stressors: 최대 " + properties.maxStressors() + "개까지 고를 수 있습니다");
        }
    }

    private CheckinResponse toResponse(Checkin checkin) {
        return new CheckinResponse(checkin.getLocalDate(), checkin.getMood(), checkin.getStressors(),
                checkin.getSupportNeed(), checkin.getStatus(), dates.inServiceZone(checkin.getUpdatedAt()));
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
