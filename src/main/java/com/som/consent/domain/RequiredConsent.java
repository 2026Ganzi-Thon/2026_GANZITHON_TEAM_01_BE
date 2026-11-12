package com.som.consent.domain;

/** 필수 약관 3종(명세 §3). API의 key 값이며 모르는 값은 400 VALIDATION_ERROR. */
public enum RequiredConsent {
    AGE_14_PLUS(ConsentType.AGE_14_PLUS),
    TERMS_OF_SERVICE(ConsentType.TERMS_OF_SERVICE),
    PRIVACY_COLLECTION(ConsentType.PRIVACY_COLLECTION);

    private final ConsentType consentType;

    RequiredConsent(ConsentType consentType) {
        this.consentType = consentType;
    }

    public ConsentType consentType() {
        return consentType;
    }
}
