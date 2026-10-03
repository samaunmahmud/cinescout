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

/** A user's place in a project's crew, and what that lets them do. */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    /** Who brought them in; null for a project's creator, or once that account is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", updatable = false)
    private User invitedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "joined_at", insertable = false, updatable = false)
    private Instant joinedAt;

    protected ProjectMember() {
    }

    public ProjectMember(Project project, User user, ProjectRole role, User invitedBy) {
        this.project = project;
        this.user = user;
        this.role = role;
        this.invitedBy = invitedBy;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public User getUser() { return user; }
    public ProjectRole getRole() { return role; }
    public User getInvitedBy() { return invitedBy; }
    public Instant getJoinedAt() { return joinedAt; }

    public void setRole(ProjectRole role) { this.role = role; }
}
