package com.som.journal.domain;

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

/** 마음 정리 기록. text는 원문 — 로그·engine에 넘기지 않는다(toString에서 제외). */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion", length = 20)
    private JournalEmotion emotion;

    @Column(name = "text")
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected JournalEntry() {}

    public static JournalEntry of(Long userId, LocalDate localDate, JournalEmotion emotion, String text,
                                  OffsetDateTime now) {
        JournalEntry entry = new JournalEntry();
        entry.userId = userId;
        entry.localDate = localDate;
        entry.emotion = emotion;
        entry.text = text;
        entry.createdAt = now;
        return entry;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public JournalEmotion getEmotion() { return emotion; }
    public String getText() { return text; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "JournalEntry[id=" + id + ", localDate=" + localDate + "]";
    }
}
