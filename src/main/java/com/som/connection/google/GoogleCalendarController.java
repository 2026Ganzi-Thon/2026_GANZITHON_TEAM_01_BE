package com.som.connection.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.connection.domain.DataSource;
import com.som.connection.dto.SignalIngestResponse;
import com.som.connection.service.SignalService;
import com.som.connection.service.SourceConsentChecker;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/google-calendar")
@ConditionalOnProperty(name = "som.google-calendar.enabled", havingValue = "true")
class GoogleCalendarController {

    private static final String PENDING = GoogleCalendarController.class.getName() + ".pending";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final GoogleCalendarClient google;
    private final SourceConsentChecker consent;
    private final SignalService signals;
    private final ServiceDates dates;

    GoogleCalendarController(GoogleCalendarClient google, SourceConsentChecker consent,
                             SignalService signals, ServiceDates dates) {
        this.google = google;
        this.consent = consent;
        this.signals = signals;
        this.dates = dates;
    }

    @PostMapping("/authorize")
    ResponseEntity<Authorization> authorize(@CurrentUserId Long userId, HttpSession session) {
        requireConsent(userId);
        Pending pending = new Pending(userId, randomToken(), randomToken(), dates.now().plusMinutes(10));
        session.setAttribute(PENDING, pending);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Authorization(google.authorize(pending.state(), challenge(pending.verifier()))));
    }

    /** OAuth 동의 직후 KST 오늘의 바쁜 시간만 가져온다. Google 토큰은 세션이나 DB에 저장하지 않는다. */
    @PostMapping("/sync")
    SignalIngestResponse sync(@CurrentUserId Long userId, HttpSession session, @Valid @RequestBody Callback callback) {
        requireConsent(userId);
        Pending pending;
        synchronized (session) {
            pending = (Pending) session.getAttribute(PENDING);
            if (pending == null || !pending.userId().equals(userId)
                    || !pending.expiresAt().isAfter(dates.now())
                    || !MessageDigest.isEqual(pending.state().getBytes(StandardCharsets.US_ASCII),
                            callback.state().getBytes(StandardCharsets.US_ASCII))) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Google 캘린더 연결을 다시 시작해 주세요.");
            }
            session.removeAttribute(PENDING);
        }
        var now = dates.now();
        var start = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        var end = start.plusDays(1);
        JsonNode busy = google.busyIntervals(callback.code(), pending.verifier(), start, end);
        return signals.ingestGoogleCalendar(userId, start.toLocalDate(), busyMinutes(busy, start, end));
    }

    private void requireConsent(Long userId) {
        if (!consent.isConsented(userId, DataSource.CALENDAR_PERSONAL)) {
            throw new ApiException(ErrorCode.SOURCE_NOT_CONSENTED);
        }
    }

    static int busyMinutes(JsonNode busy, OffsetDateTime start, OffsetDateTime end) {
        var intervals = new ArrayList<Interval>();
        try {
            for (JsonNode item : busy) {
                var from = OffsetDateTime.parse(item.path("start").asText());
                var until = OffsetDateTime.parse(item.path("end").asText());
                if (from.isBefore(start)) from = start;
                if (until.isAfter(end)) until = end;
                if (from.isBefore(until)) intervals.add(new Interval(from, until));
            }
        } catch (DateTimeParseException e) {
            throw GoogleCalendarClient.unavailable();
        }

        intervals.sort(Comparator.comparing(Interval::start));
        Duration occupied = Duration.ZERO;
        OffsetDateTime accountedUntil = start;
        for (Interval interval : intervals) {
            if (interval.end().isAfter(accountedUntil)) {
                var from = interval.start().isAfter(accountedUntil) ? interval.start() : accountedUntil;
                occupied = occupied.plus(Duration.between(from, interval.end()));
                accountedUntil = interval.end();
            }
        }
        return Math.toIntExact(occupied.toMinutes());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        try {
            return URL_ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Interval(OffsetDateTime start, OffsetDateTime end) {}
    private record Pending(Long userId, String state, String verifier, OffsetDateTime expiresAt) {}
    record Authorization(URI authorizationUrl) {}
    record Callback(@NotBlank @Size(max = 2048) String code, @NotBlank @Size(max = 128) String state) {
        @Override
        public String toString() {
            return "GoogleCalendarCallback[redacted]";
        }
    }
}
