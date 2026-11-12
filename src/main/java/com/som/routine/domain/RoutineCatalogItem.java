package com.som.routine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** 루틴 카탈로그 항목(Flyway 시드, 읽기 전용). */
@Entity
@Immutable
@Table(name = "routine_catalog")
public class RoutineCatalogItem {

    @Id
    @Column(name = "routine_id")
    private String routineId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle", nullable = false)
    private String subtitle;

    @Column(name = "instruction", nullable = false)
    private String instruction;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "asset_key", nullable = false)
    private String assetKey;

    protected RoutineCatalogItem() {}

    public String getRoutineId() { return routineId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getInstruction() { return instruction; }
    public int getDurationSec() { return durationSec; }
    public int getDisplayOrder() { return displayOrder; }
    public String getAssetKey() { return assetKey; }
}
