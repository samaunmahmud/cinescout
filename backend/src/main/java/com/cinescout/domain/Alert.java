package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An alert for one person. Made by the jobs (inserted with SQL, see {@code AlertRepository}); read and marked read here.
 * The payload carries the facts the alert's words are made of: names, dates and numbers, never notes or scores.
 */
@Entity
@Table(name = "alerts")
public class Alert extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AlertKind kind;

    @Column(name = "scene_id", updatable = false)
    private UUID sceneId;

    @Column(name = "location_id", updatable = false)
    private UUID locationId;

    @Column(name = "draft_id", updatable = false)
    private UUID draftId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    @Column(name = "dedupe_key", nullable = false, updatable = false)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    protected Alert() {
    }

    public UUID getUserId() { return userId; }
    public Project getProject() { return project; }
    public AlertKind getKind() { return kind; }
    public UUID getSceneId() { return sceneId; }
    public UUID getLocationId() { return locationId; }
    public UUID getDraftId() { return draftId; }
    public Map<String, Object> getPayload() { return payload; }
    public String getDedupeKey() { return dedupeKey; }
    public Instant getReadAt() { return readAt; }
}
