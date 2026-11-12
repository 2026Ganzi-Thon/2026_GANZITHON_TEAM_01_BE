package com.som.settings.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.settings.NotificationProperties;
import com.som.settings.domain.NotificationSettings;
import com.som.settings.domain.NotificationSlot;
import com.som.settings.dto.NotificationSettingsResponse;
import com.som.settings.repository.NotificationSettingsRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 설정 저장·조회(A_SPEC §10). 발송은 P1이라 만들지 않는다. */
@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationSettingsService {

    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> FIELDS = Set.of("enabled", "slot", "time");

    private final NotificationSettingsRepository settings;
    private final ServiceDates dates;
    private final NotificationProperties properties;

    public NotificationSettingsService(NotificationSettingsRepository settings, ServiceDates dates,
                                       NotificationProperties properties) {
        this.settings = settings;
        this.dates = dates;
        this.properties = properties;
    }

    /** 행이 없으면 기본값을 돌려주고 저장하지 않는다. */
    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(Long userId) {
        return settings.findById(userId).map(this::toResponse)
                .orElseGet(() -> new NotificationSettingsResponse(properties.defaultEnabled(), null, null));
    }

    /** 필드 없음 = 그대로, JSON null = 지움(enabled는 null 불가). 정의되지 않은 필드는 400(값은 메시지에 넣지 않음). */
    @Transactional
    public NotificationSettingsResponse update(Long userId, ObjectNode body) {
        for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!FIELDS.contains(field)) {
                throw invalid(field + ": 허용되지 않는 필드입니다");
            }
        }
        Boolean enabled = null;
        if (body.has("enabled")) {
            if (!body.get("enabled").isBoolean()) {
                throw invalid("enabled: true 또는 false여야 합니다");
            }
            enabled = body.get("enabled").booleanValue();
        }
        NotificationSlot slot = body.has("slot") ? parseSlot(body.get("slot")) : null;
        LocalTime time = body.has("time") ? parseTime(body.get("time")) : null;

        NotificationSettings current = settings.findById(userId)
                .orElseGet(() -> NotificationSettings.defaults(userId, properties.defaultEnabled()));
        current.apply(enabled, slot, body.has("slot"), time, body.has("time"), dates.now());
        return toResponse(settings.save(current));
    }

    private static NotificationSlot parseSlot(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        try {
            return NotificationSlot.valueOf(node.textValue());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("slot: 형식이 올바르지 않습니다");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static LocalTime parseTime(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        String value = node.isTextual() ? node.textValue() : "";
        if (!HH_MM.matcher(value).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "time: HH:mm 형식이어야 합니다");
        }
        return LocalTime.parse(value, FORMAT);
    }

    private NotificationSettingsResponse toResponse(NotificationSettings s) {
        return new NotificationSettingsResponse(s.isEnabled(), s.getSlot(),
                Optional.ofNullable(s.getTime()).map(FORMAT::format).orElse(null));
    }
}
