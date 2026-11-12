package com.som.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 대화 메시지. content는 원문 — 로그에 남기지 않는다(toString에서 제외). */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "thread_date", nullable = false, updatable = false)
    private LocalDate threadDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10, updatable = false)
    private MessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30, updatable = false)
    private MessageType messageType;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "payload", updatable = false)
    private String payload;

    @Column(name = "risk_flag", nullable = false, updatable = false)
    private boolean riskFlag;

    @Column(name = "is_greeting", nullable = false, updatable = false)
    private boolean greeting;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ChatMessage() {}

    public static ChatMessage of(Long userId, LocalDate threadDate, MessageRole role, MessageType type,
                                 String content, String payload, boolean riskFlag, boolean greeting,
                                 OffsetDateTime now) {
        ChatMessage m = new ChatMessage();
        m.userId = userId;
        m.threadDate = threadDate;
        m.role = role;
        m.messageType = type;
        m.content = content;
        m.payload = payload;
        m.riskFlag = riskFlag;
        m.greeting = greeting;
        m.createdAt = now;
        return m;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getThreadDate() { return threadDate; }
    public MessageRole getRole() { return role; }
    public MessageType getMessageType() { return messageType; }
    public String getContent() { return content; }
    public String getPayload() { return payload; }
    public boolean isRiskFlag() { return riskFlag; }
    public boolean isGreeting() { return greeting; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "ChatMessage[id=" + id + ", role=" + role + ", type=" + messageType + "]";
    }
}
