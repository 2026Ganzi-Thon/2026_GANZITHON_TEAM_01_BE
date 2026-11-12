package com.som.state.domain;

import com.som.engine.fatigue.FatigueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 사용자·날짜별 engine 계산 결과. 값은 engine 결과 그대로이고 A에서 고치지 않는다. */
@Entity
@Table(name = "fatigue_snapshots")
public class FatigueSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Column(name = "score")
    private Integer score;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "level")
    private Integer level;

    @Column(name = "wool_display_kg", precision = 4, scale = 2)
    private BigDecimal woolDisplayKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FatigueStatus status;

    @Column(name = "status_text", length = 100)
    private String statusText;

    @Column(name = "sub_text", length = 200)
    private String subText;

    @Column(name = "asset_key", length = 100)
    private String assetKey;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    /** JSON 배열 문자열(ADR-004: 배열 타입 대신 표준 매핑). */
    @Column(name = "used_sources", nullable = false)
    private String usedSources;

    @Column(name = "signal_changes", nullable = false)
    private String signalChanges;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FatigueSnapshot() {}

    public static FatigueSnapshot of(Long userId, LocalDate localDate) {
        FatigueSnapshot snapshot = new FatigueSnapshot();
        snapshot.userId = userId;
        snapshot.localDate = localDate;
        return snapshot;
    }

    /** engine 결과로 교체한다(정상 값이므로 stale 해제). */
    public void replace(Integer score, Integer level, BigDecimal woolDisplayKg, FatigueStatus status,
                        String statusText, String subText, String assetKey, String usedSourcesJson,
                        String algorithmVersion, OffsetDateTime calculatedAt, OffsetDateTime now, String signalChanges) {
        this.score = score;
        this.level = level;
        this.woolDisplayKg = woolDisplayKg;
        this.status = status;
        this.statusText = statusText;
        this.subText = subText;
        this.assetKey = assetKey;
        this.usedSources = usedSourcesJson;
        this.signalChanges = signalChanges;
        this.algorithmVersion = algorithmVersion;
        this.calculatedAt = calculatedAt;
        this.stale = false;
        this.updatedAt = now;
    }

    /** engine 실패: 값은 그대로 두고 오래된 값임을 표시한다. */
    public void markStale(OffsetDateTime now) {
        this.stale = true;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public Integer getScore() { return score; }
    public Integer getLevel() { return level; }
    public BigDecimal getWoolDisplayKg() { return woolDisplayKg; }
    public FatigueStatus getStatus() { return status; }
    public String getStatusText() { return statusText; }
    public String getSubText() { return subText; }
    public String getAssetKey() { return assetKey; }
    public String getSignalChanges() { return signalChanges; }
    public String getUsedSources() { return usedSources; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public boolean isStale() { return stale; }
    public OffsetDateTime getCalculatedAt() { return calculatedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
