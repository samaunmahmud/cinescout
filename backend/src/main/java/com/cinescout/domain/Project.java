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

@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    /** The project's one OWNER member, kept in step with project_members when ownership is handed over. */
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    private String description;

    /** Free-text region scouting searches in, e.g. "Brooklyn, New York"; null until set. */
    @Column(name = "location_area")
    private String locationArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    /** The token of the project's shared call sheet link; null while it is not shared. */
    @Column(name = "call_sheet_token")
    private String callSheetToken;

    /** The scouting filters a run keeps to unless it brings its own; null until set. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scout_filters")
    private ScoutFilters scoutFilters;

    protected Project() {
    }

    public Project(User owner, String title, String description) {
        this.owner = owner;
        this.title = title;
        this.description = description;
    }

    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocationArea() { return locationArea; }
    public ProjectStatus getStatus() { return status; }
    public String getCallSheetToken() { return callSheetToken; }
    public ScoutFilters getScoutFilters() { return scoutFilters; }

    public void setTitle(String title) { this.title = title; }
    public void setCallSheetToken(String callSheetToken) { this.callSheetToken = callSheetToken; }
    public void setScoutFilters(ScoutFilters scoutFilters) { this.scoutFilters = scoutFilters; }
    public void setDescription(String description) { this.description = description; }

    /** Blank means "not set": it is stored as null, which the database constraint requires. */
    public void setLocationArea(String locationArea) {
        this.locationArea = locationArea == null || locationArea.isBlank() ? null : locationArea.strip();
    }

    public void setStatus(ProjectStatus status) { this.status = status; }
    public void setOwner(User owner) { this.owner = owner; }
}
