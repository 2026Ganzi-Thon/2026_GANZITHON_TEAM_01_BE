package com.som.connection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.connection.SourceConsentProperties;
import com.som.connection.domain.Connection;
import com.som.connection.domain.ConnectionProvider;
import com.som.connection.domain.ConnectionStatus;
import com.som.connection.domain.DataSource;
import com.som.connection.dto.ConnectionResponse;
import com.som.connection.repository.ConnectionRepository;
import com.som.connection.repository.DailySignalRepository;
import com.som.consent.domain.ConsentType;
import com.som.consent.domain.UserConsent;
import com.som.consent.repository.UserConsentRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택 연동 동의 + 연결 상태. 동의 변경은 user_consents에 새 행 INSERT로만 기록하고(고지 내용은 서버 설정값),
 * 현재 상태와 같으면 아무것도 쓰지 않는다(멱등). 동의 여부의 기준은 최신 동의 이력이다.
 */
@Service
@EnableConfigurationProperties(SourceConsentProperties.class)
public class ConnectionService implements SourceConsentChecker {

    private final ConnectionRepository connections;
    private final DailySignalRepository signals;
    private final UserConsentRepository consents;
    private final SourceConsentProperties properties;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConnectionService(ConnectionRepository connections, DailySignalRepository signals,
                             UserConsentRepository consents,
                             SourceConsentProperties properties, ApplicationEventPublisher events,
                             ObjectMapper objectMapper, Clock clock) {
        this.connections = connections;
        this.signals = signals;
        this.consents = consents;
        this.properties = properties;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        for (DataSource source : DataSource.values()) {
            SourceConsentProperties.Notice notice = properties.sources() == null ? null : properties.sources().get(source);
            if (notice == null || notice.policyVersion() == null || notice.items() == null || notice.items().isEmpty()
                    || notice.purpose() == null || notice.retention() == null || notice.sensitive() == null) {
                throw new IllegalStateException("som.consent.sources." + source + " is not fully configured");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(Long userId) {
        Map<DataSource, Connection> bySource = connections.findByUserId(userId).stream()
                .collect(Collectors.toMap(Connection::getSource, Function.identity()));
        return Arrays.stream(DataSource.values())
                .map(source -> toResponse(source, Optional.ofNullable(bySource.get(source))))
                .toList();
    }

    @Transactional
    public ConnectionResponse connect(Long userId, DataSource source) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!isConsented(userId, source)) {
            consents.save(consentRow(userId, source, true, now));
        }
        Connection connection = connections.findByUserIdAndSource(userId, source)
                .orElseGet(() -> Connection.newFor(userId, source));
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            connection.connect(ConnectionProvider.DEMO, now);
            connection = connections.save(connection);
        }
        return toResponse(source, Optional.of(connection));
    }

    /**
     * 철회 순서(명세 §5): 동의 철회 기록(이후 수집·계산 차단) → 보유 데이터 삭제(SourceRevokedEvent 리스너, step 9)
     * → 연결 해제. 모두 한 트랜잭션이다. 이력이 없거나 이미 철회된 상태면 아무것도 쓰지 않는다.
     */
    @Transactional
    public ConnectionResponse revoke(Long userId, DataSource source) {
        signals.lockSource(userId, source); // 진행 중인 수신이 끝난 뒤 철회한다(SignalService.ingest와 같은 잠금)
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (isConsented(userId, source)) {
            consents.save(consentRow(userId, source, false, now));
            events.publishEvent(new SourceRevokedEvent(userId, source));
        }
        Optional<Connection> existing = connections.findByUserIdAndSource(userId, source);
        existing.filter(c -> c.getStatus() != ConnectionStatus.REVOKED).ifPresent(c -> {
            c.revoke(now);
            connections.save(c);
        });
        return toResponse(source, existing);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConsented(Long userId, DataSource source) {
        return consents.findFirstByUserIdAndConsentTypeAndSourceOrderByChangedAtDescIdDesc(
                        userId, ConsentType.DATA_SOURCE, source.name())
                .map(UserConsent::isGranted)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DataSource> consentedSources(Long userId) {
        Set<DataSource> result = EnumSet.noneOf(DataSource.class);
        for (DataSource source : DataSource.values()) {
            if (isConsented(userId, source)) {
                result.add(source);
            }
        }
        return result;
    }

    private UserConsent consentRow(Long userId, DataSource source, boolean granted, OffsetDateTime now) {
        SourceConsentProperties.Notice notice = notice(source);
        return UserConsent.dataSource(userId, source.name(), itemsJson(notice.items()), notice.purpose(),
                notice.retention(), granted, notice.policyVersion(), now);
    }

    private ConnectionResponse toResponse(DataSource source, Optional<Connection> connection) {
        SourceConsentProperties.Notice notice = notice(source);
        ConnectionStatus status = connection.map(Connection::getStatus).orElse(ConnectionStatus.NOT_CONNECTED);
        OffsetDateTime connectedAt = status == ConnectionStatus.CONNECTED
                ? connection.get().getConnectedAt().atZoneSameInstant(clock.getZone()).toOffsetDateTime()
                : null;
        return new ConnectionResponse(source, status, notice.sensitive(), notice.items(), notice.purpose(),
                notice.retention(), notice.policyVersion(), connectedAt);
    }

    private SourceConsentProperties.Notice notice(DataSource source) {
        return properties.sources().get(source);
    }

    private String itemsJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize consent items", e);
        }
    }
}
