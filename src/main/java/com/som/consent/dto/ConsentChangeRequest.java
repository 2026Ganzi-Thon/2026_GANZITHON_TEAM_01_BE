package com.som.consent.dto;

import com.som.consent.domain.RequiredConsent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** PATCH /api/v1/me/consents. policyVersion·changedAt은 받지 않는다(서버가 정한다). */
public record ConsentChangeRequest(@NotEmpty List<@NotNull @Valid Item> consents) {
    public record Item(@NotNull RequiredConsent key, @NotNull Boolean granted) {}
}
