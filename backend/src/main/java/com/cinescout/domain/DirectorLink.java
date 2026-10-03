package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * A link that shows someone without an account the shortlisted venues of a project, or of one scene when
 * {@code scene} is set, for them to answer. The token is the permission; deleting the link withdraws it.
 */
@Entity
@Table(name = "director_links")
public class DirectorLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", updatable = false)
    private Scene scene;

    @Column(nullable = false, updatable = false)
    private String token;

    @Column(name = "show_private", nullable = false, updatable = false)
    private boolean showPrivate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DirectorLink() {
    }

    public DirectorLink(Project project, Scene scene, String token, boolean showPrivate, User createdBy) {
        this.project = project;
        this.scene = scene;
        this.token = token;
        this.showPrivate = showPrivate;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    /** Null for a link to the whole project. */
    public Scene getScene() {
        return scene;
    }

    public String getToken() {
        return token;
    }

    public boolean isShowPrivate() {
        return showPrivate;
    }

    /** Null once that account is deleted. */
    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
