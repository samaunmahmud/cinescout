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
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation for someone without an account to join a project's crew, by a link the owner passes on. Only the
 * hash of the link's token is stored. One email address, one use, a limited life.
 */
@Entity
@Table(name = "project_invites")
public class ProjectInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ProjectRole role;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", updatable = false)
    private User invitedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    protected ProjectInvite() {
    }

    public ProjectInvite(Project project, String email, ProjectRole role, String tokenHash, User invitedBy, Instant expiresAt) {
        this.project = project;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getEmail() { return email; }
    public ProjectRole getRole() { return role; }
    public User getInvitedBy() { return invitedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }

    public boolean isOpen(Instant now) {
        return acceptedAt == null && now.isBefore(expiresAt);
    }

    public void accept(User user, Instant when) {
        this.acceptedBy = user;
        this.acceptedAt = when;
    }
}
