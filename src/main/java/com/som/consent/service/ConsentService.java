package com.som.consent.service;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.consent.ConsentProperties;
import com.som.consent.domain.RequiredConsent;
import com.som.consent.domain.UserConsent;
import com.som.consent.dto.ConsentChangeRequest;
import com.som.consent.dto.ConsentsResponse;
import com.som.consent.repository.UserConsentRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 필수 약관 동의. 변경은 새 행 INSERT로만 기록하고, 현재 상태와 같은 값이면 아무것도 쓰지 않는다. */
@Service
@EnableConfigurationProperties(ConsentProperties.class)
public class ConsentService implements RequiredConsentChecker {

    private final UserConsentRepository consents;
    private final ConsentProperties properties;
    private final Clock clock;

    public ConsentService(UserConsentRepository consents, ConsentProperties properties, Clock clock) {
        this.consents = consents;
        this.properties = properties;
        this.clock = clock;
        for (RequiredConsent key : RequiredConsent.values()) {
            if (policyVersionOf(key) == null) {
                throw new IllegalStateException("som.consent.required." + key + ".policy-version is not set");
            }
        }
    }

    @Transactional(readOnly = true)
    public ConsentsResponse get(Long userId) {
        List<ConsentsResponse.RequiredItem> items = Arrays.stream(RequiredConsent.values())
                .map(key -> toItem(key, current(userId, key)))
                .toList();
        boolean completed = items.stream().allMatch(ConsentsResponse.RequiredItem::granted);
        return new ConsentsResponse(items, completed);
    }

    @Transactional
    public ConsentsResponse change(Long userId, ConsentChangeRequest request) {
        Set<RequiredConsent> seen = EnumSet.noneOf(RequiredConsent.class);
        for (ConsentChangeRequest.Item item : request.consents()) {
            if (!seen.add(item.key())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "consents: 같은 key가 중복됐습니다");
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (ConsentChangeRequest.Item item : request.consents()) {
            boolean currentlyGranted = current(userId, item.key()).map(UserConsent::isGranted).orElse(false);
            if (currentlyGranted != item.granted()) {
                consents.save(UserConsent.required(userId, item.key(), item.granted(), policyVersionOf(item.key()), now));
            }
        }
        return get(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompleted(Long userId) {
        return Arrays.stream(RequiredConsent.values())
                .allMatch(key -> current(userId, key).map(UserConsent::isGranted).orElse(false));
    }

    private Optional<UserConsent> current(Long userId, RequiredConsent key) {
        return consents.findFirstByUserIdAndConsentTypeAndSourceIsNullOrderByChangedAtDescIdDesc(
                userId, key.consentType());
    }

    private ConsentsResponse.RequiredItem toItem(RequiredConsent key, Optional<UserConsent> row) {
        return row.map(c -> new ConsentsResponse.RequiredItem(key, c.isGranted(), c.getPolicyVersion(),
                        c.getChangedAt().atZoneSameInstant(clock.getZone()).toOffsetDateTime()))
                .orElseGet(() -> new ConsentsResponse.RequiredItem(key, false, null, null));
    }

    private String policyVersionOf(RequiredConsent key) {
        ConsentProperties.Policy policy = properties.required() == null ? null : properties.required().get(key);
        return policy == null ? null : policy.policyVersion();
    }
}
