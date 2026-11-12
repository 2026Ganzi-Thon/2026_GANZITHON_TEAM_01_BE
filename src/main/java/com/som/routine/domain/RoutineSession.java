package com.som.routine.domain;

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

/** 사용자·루틴·날짜별 실행 기록(1행). 전이 규칙은 RoutineSessionService가 정한다. */
@Entity
@Table(name = "routine_sessions")
public class RoutineSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "routine_id", nullable = false, updatable = false)
    private String routineId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutineSessionStatus status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RoutineSession() {}

    public static RoutineSession of(Long userId, String routineId, LocalDate localDate) {
        RoutineSession session = new RoutineSession();
        session.userId = userId;
        session.routineId = routineId;
        session.localDate = localDate;
        return session;
    }

    public void start(OffsetDateTime now) {
        this.status = RoutineSessionStatus.STARTED;
        this.startedAt = now;
        this.finishedAt = null;
        this.updatedAt = now;
    }

    public void complete(OffsetDateTime now) {
        this.status = RoutineSessionStatus.COMPLETED;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void skip(OffsetDateTime now) {
        this.status = RoutineSessionStatus.SKIPPED;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRoutineId() { return routineId; }
    public LocalDate getLocalDate() { return localDate; }
    public RoutineSessionStatus getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
