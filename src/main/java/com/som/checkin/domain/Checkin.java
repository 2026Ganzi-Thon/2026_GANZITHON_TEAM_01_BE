package com.som.checkin.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** 하루 체크인(사용자·날짜당 1행). local_date는 서버가 KST로 산출한 값만 받는다. */
@Entity
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mood", length = 20)
    private Mood mood;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "checkin_stressors", joinColumns = @JoinColumn(name = "checkin_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "stressor", nullable = false, length = 20)
    private List<Stressor> stressors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "support_need", length = 20)
    private SupportNeed supportNeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CheckinStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Checkin() {}

    public static Checkin create(Long userId, LocalDate localDate, Mood mood, List<Stressor> stressors,
                                 SupportNeed supportNeed, OffsetDateTime now) {
        Checkin checkin = new Checkin();
        checkin.userId = userId;
        checkin.localDate = localDate;
        checkin.createdAt = now;
        checkin.update(mood, stressors, supportNeed, now);
        return checkin;
    }

    /** 같은 날 재제출: 최신 값으로 덮어쓴다. */
    public void update(Mood mood, List<Stressor> stressors, SupportNeed supportNeed, OffsetDateTime now) {
        this.mood = mood;
        this.stressors.clear();
        if (stressors != null) {
            this.stressors.addAll(stressors);
        }
        this.supportNeed = supportNeed;
        this.status = mood == null && this.stressors.isEmpty() && supportNeed == null
                ? CheckinStatus.SKIPPED : CheckinStatus.SUBMITTED;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public Mood getMood() { return mood; }
    public List<Stressor> getStressors() { return List.copyOf(stressors); }
    public SupportNeed getSupportNeed() { return supportNeed; }
    public CheckinStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
