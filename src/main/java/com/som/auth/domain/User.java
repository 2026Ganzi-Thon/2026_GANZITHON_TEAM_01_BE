package com.som.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 64, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** engine에 넘기는 가명 ID. userId·loginId에서 유도하지 않는다(A_SPEC §12). */
    @Column(name = "subject_key", nullable = false, length = 64, unique = true)
    private String subjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected User() {}

    public static User register(String loginId, String passwordHash, String subjectKey, OffsetDateTime now) {
        User user = new User();
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        user.subjectKey = subjectKey;
        user.authProvider = AuthProvider.LOCAL;
        user.createdAt = now;
        return user;
    }

    public void recordLogin(OffsetDateTime now) {
        this.lastLoginAt = now;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getSubjectKey() { return subjectKey; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }

    /** passwordHash는 넣지 않는다(로그 금지 규칙). */
    @Override
    public String toString() {
        return "User{id=" + id + ", loginId=" + loginId + ", authProvider=" + authProvider + "}";
    }
}
