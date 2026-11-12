package com.som.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** 도움 받을 곳(시드 데이터, 읽기 전용). actionValue는 확인 전이면 null. */
@Entity
@Immutable
@Table(name = "help_resources")
public class HelpResource {

    @Id
    private Long id;

    @Column(name = "situation", nullable = false)
    private String situation;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "action_value")
    private String actionValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected HelpResource() {}

    public Long getId() { return id; }
    public String getSituation() { return situation; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getActionType() { return actionType; }
    public String getActionValue() { return actionValue; }
    public int getDisplayOrder() { return displayOrder; }
}
