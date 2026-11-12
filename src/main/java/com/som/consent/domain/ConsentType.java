package com.som.consent.domain;

/** user_consents.consent_type. 필수 약관 3종 + 선택 연동 동의(DATA_SOURCE, step 8). */
public enum ConsentType {
    AGE_14_PLUS,
    TERMS_OF_SERVICE,
    PRIVACY_COLLECTION,
    DATA_SOURCE
}
