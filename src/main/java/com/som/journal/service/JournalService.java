package com.som.journal.service;

import com.som.auth.repository.UserRepository;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.engine.routine.WoolTrimEngine;
import com.som.journal.JournalProperties;
import com.som.journal.domain.JournalEntry;
import com.som.journal.dto.JournalRequest;
import com.som.journal.dto.JournalResponse;
import com.som.journal.repository.JournalEntryRepository;
import com.som.journal.repository.WoolTrimEventRepository;
import com.som.routine.service.RoutineSessionService;
import com.som.state.service.EngineGateway;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마음 정리(A_SPEC §9). 저장하면 그날 털 깎임 연출(wool_trim_events)을 한 번 기록한다.
 * - 기록은 engine에 넘기지 않는다(CRITICAL). 텍스트는 로그·예외 메시지에 넣지 않는다.
 * - 털 깎임은 연출 값일 뿐 스냅샷(score·level)을 바꾸지 않는다(CRITICAL).
 */
@Service
@EnableConfigurationProperties(JournalProperties.class)
public class JournalService {

    static final String TRIM_SOURCE = "JOURNAL";

    private final JournalEntryRepository entries;
    private final WoolTrimEventRepository woolTrims;
    private final RoutineSessionService routines;
    private final ServiceDates dates;
    private final JournalProperties properties;
    private final Optional<WoolTrimEngine> trimEngine;
    private final UserRepository users;
    private final EngineGateway gateway;

    public JournalService(JournalEntryRepository entries, WoolTrimEventRepository woolTrims,
                          RoutineSessionService routines, ServiceDates dates, JournalProperties properties,
                          Optional<WoolTrimEngine> trimEngine, UserRepository users, EngineGateway gateway) {
        this.entries = entries;
        this.woolTrims = woolTrims;
        this.routines = routines;
        this.dates = dates;
        this.properties = properties;
        this.trimEngine = trimEngine;
        this.users = users;
        this.gateway = gateway;
    }

    @Transactional
    public JournalResponse write(Long userId, JournalRequest request) {
        LocalDate today = dates.today();
        if (!routines.journalUnlocked(userId, today)) {
            throw new ApiException(ErrorCode.JOURNAL_LOCKED);
        }
        String text = normalize(request.text());
        // TODO(DECISION): 둘 다 비어도 완료 허용 여부(A_SPEC §9) — 기본 허용(som.journal.allow-empty)
        if (!properties.allowEmpty() && request.emotion() == null && text == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "emotion: 감정이나 기록 중 하나는 필요합니다");
        }
        OffsetDateTime now = dates.now();
        JournalEntry saved = entries.saveAndFlush(JournalEntry.of(userId, today, request.emotion(), text, now));
        boolean trimmed = woolTrims.exists(userId, today);
        if (!trimmed && shouldTrim(userId, today)) {
            woolTrims.recordOnce(userId, today, TRIM_SOURCE, now);
            trimmed = true;
        }
        return toResponse(saved, trimmed);
    }

    private boolean shouldTrim(Long userId, LocalDate date) {
        return trimEngine.map(engine -> {
            String subjectKey = users.findById(userId).map(user -> user.getSubjectKey())
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
            return gateway.call("routines.wool-trim", () -> engine.shouldPlay(subjectKey, date),
                    Objects::nonNull).orElse(true);
        }).orElse(true);
    }

    @Transactional(readOnly = true)
    public List<JournalResponse> list(Long userId, LocalDate date) {
        boolean trimmed = woolTrims.exists(userId, date);
        return entries.findByUserIdAndLocalDateOrderByCreatedAtAscIdAsc(userId, date).stream()
                .map(e -> toResponse(e, trimmed)).toList();
    }

    /** trim 후 빈 문자열은 null. 길이 초과·제어문자(\t·\n·\r 제외)는 400. 메시지에 원문을 넣지 않는다. */
    // TODO(DECISION): 자유 기록 최대 길이(A_SPEC §9) — 기본 1000자(som.journal.max-text-length)
    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > properties.maxTextLength()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "text: 최대 " + properties.maxTextLength() + "자까지 쓸 수 있습니다");
        }
        if (trimmed.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r')) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "text: 쓸 수 없는 문자가 있습니다");
        }
        return trimmed;
    }

    private JournalResponse toResponse(JournalEntry entry, boolean trimmed) {
        return new JournalResponse(entry.getId(), entry.getLocalDate(), entry.getEmotion(), entry.getText(),
                dates.inServiceZone(entry.getCreatedAt()), trimmed);
    }
}
