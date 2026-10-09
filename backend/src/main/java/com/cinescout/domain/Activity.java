package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One line of a project's activity log. Written once, never changed: every column is insert-only and the table refuses updates. */
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ActivityKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ActivityVerb verb;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private ActivityTarget targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Activity() {
    }

    public Activity(Project project, UUID actorId, String actorName, ActivityVerb verb, ActivityTarget targetType, UUID targetId,
                    Map<String, Object> payload) {
        this.project = project;
        this.actorId = actorId;
        this.actorName = actorName;
        this.kind = verb.kind();
        this.verb = verb;
        this.targetType = targetType;
        this.targetId = targetId;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Project getProject() {
        return project;
    }

    public String getActorName() {
        return actorName;
    }

    public ActivityKind getKind() {
        return kind;
    }

    public ActivityVerb getVerb() {
        return verb;
    }

    public ActivityTarget getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Map<String, Object> getPayload() {
        return payload == null ? Map.of() : payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
