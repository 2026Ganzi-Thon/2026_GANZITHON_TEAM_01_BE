package com.som.consent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;

/**
 * 동의 이력 한 행. 만들어진 뒤에는 바뀌지 않는다(@Immutable: Hibernate가 UPDATE를 내지 않음).
 * 상태 변경은 새 행으로 기록한다(명세 §3 덮어쓰기 금지).
 */
@Entity
@Immutable
@Table(name = "user_consents")
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40, updatable = false)
    private ConsentType consentType;

    /** DATA_SOURCE일 때만 값이 있다(com.som.connection.domain.DataSource 이름). */
    @Column(name = "source", length = 40, updatable = false)
    private String source;

    @Column(name = "items", length = 1000, updatable = false)
    private String items;

    @Column(name = "purpose", length = 500, updatable = false)
    private String purpose;

    @Column(name = "retention", length = 200, updatable = false)
    private String retention;

    @Column(name = "is_granted", nullable = false, updatable = false)
    private boolean granted;

    @Column(name = "policy_version", nullable = false, length = 40, updatable = false)
    private String policyVersion;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    protected UserConsent() {}

    public static UserConsent required(Long userId, RequiredConsent key, boolean granted,
                                       String policyVersion, OffsetDateTime changedAt) {
        UserConsent consent = new UserConsent();
        consent.userId = userId;
        consent.consentType = key.consentType();
        consent.granted = granted;
        consent.policyVersion = policyVersion;
        consent.changedAt = changedAt;
        return consent;
    }

    /** 선택 연동 동의 한 행. items·purpose·retention·policyVersion은 서버 설정값(고지 내용)을 넣는다. */
    public static UserConsent dataSource(Long userId, String source, String itemsJson, String purpose,
                                         String retention, boolean granted, String policyVersion,
                                         OffsetDateTime changedAt) {
        UserConsent consent = new UserConsent();
        consent.userId = userId;
        consent.consentType = ConsentType.DATA_SOURCE;
        consent.source = source;
        consent.items = itemsJson;
        consent.purpose = purpose;
        consent.retention = retention;
        consent.granted = granted;
        consent.policyVersion = policyVersion;
        consent.changedAt = changedAt;
        return consent;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ConsentType getConsentType() { return consentType; }
    public String getSource() { return source; }
    public String getItems() { return items; }
    public String getPurpose() { return purpose; }
    public String getRetention() { return retention; }
    public boolean isGranted() { return granted; }
    public String getPolicyVersion() { return policyVersion; }
    public OffsetDateTime getChangedAt() { return changedAt; }
}
