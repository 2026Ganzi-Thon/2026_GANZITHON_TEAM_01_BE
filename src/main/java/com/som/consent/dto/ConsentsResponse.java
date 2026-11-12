package com.som.consent.dto;

import com.som.consent.domain.RequiredConsent;
import java.time.OffsetDateTime;
import java.util.List;

/** GET·PATCH /api/v1/me/consents 응답. 이력이 없는 항목은 granted=false, policyVersion·changedAt=null. */
public record ConsentsResponse(List<RequiredItem> required, boolean requiredCompleted) {
    public record RequiredItem(RequiredConsent key, boolean granted, String policyVersion, OffsetDateTime changedAt) {}
}
