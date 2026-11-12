package com.som.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 사용자별 알림 설정(1행). slot=NONE이면 enabled는 항상 false. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", length = 20)
    private NotificationSlot slot;

    @Column(name = "time")
    private LocalTime time;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationSettings() {}

    public static NotificationSettings defaults(Long userId, boolean enabled) {
        NotificationSettings s = new NotificationSettings();
        s.userId = userId;
        s.enabled = enabled;
        return s;
    }

    public void apply(Boolean enabled, NotificationSlot slot, boolean slotSent, LocalTime time, boolean timeSent,
                      OffsetDateTime now) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (slotSent) {
            this.slot = slot;
        }
        if (timeSent) {
            this.time = time;
        }
        if (this.slot == NotificationSlot.NONE) {
            this.enabled = false; // '알림 없이'(A_SPEC §4)
        }
        this.updatedAt = now;
    }

    public Long getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public NotificationSlot getSlot() { return slot; }
    public LocalTime getTime() { return time; }
}
