package com.som.connection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 사용자·소스별 현재 연결 상태(이력 아님). 동의 이력은 user_consents에 INSERT로 쌓는다. */
@Entity
@Table(name = "connections")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40, updatable = false)
    private DataSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private ConnectionProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(name = "connected_at")
    private OffsetDateTime connectedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected Connection() {}

    public static Connection newFor(Long userId, DataSource source) {
        Connection connection = new Connection();
        connection.userId = userId;
        connection.source = source;
        return connection;
    }

    public void connect(ConnectionProvider provider, OffsetDateTime now) {
        this.provider = provider;
        this.status = ConnectionStatus.CONNECTED;
        this.connectedAt = now;
        this.revokedAt = null;
    }

    public void revoke(OffsetDateTime now) {
        this.status = ConnectionStatus.REVOKED;
        this.revokedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public DataSource getSource() { return source; }
    public ConnectionProvider getProvider() { return provider; }
    public ConnectionStatus getStatus() { return status; }
    public OffsetDateTime getConnectedAt() { return connectedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
}
