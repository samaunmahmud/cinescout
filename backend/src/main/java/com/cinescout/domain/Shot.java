package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

/** One shot of a scene's shot list. {@code position} orders the list; gaps are fine. */
@Entity
@Table(name = "shots")
public class Shot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false, updatable = false)
    private Scene scene;

    /** Null for the scene's confirmed venue. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private ShotSize size;

    /** Which way the camera faces, in degrees from north; null when not planned yet. */
    @Column(name = "camera_bearing")
    private Short cameraBearing;

    @Column(name = "planned_time")
    private LocalTime plannedTime;

    @Column(nullable = false)
    private boolean done;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    protected Shot() {
    }

    public Shot(Scene scene, int position, User createdBy) {
        this.scene = scene;
        this.position = position;
        this.createdBy = createdBy;
    }

    public Scene getScene() { return scene; }
    public Location getLocation() { return location; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public ShotSize getSize() { return size; }
    public Short getCameraBearing() { return cameraBearing; }
    public LocalTime getPlannedTime() { return plannedTime; }
    public boolean isDone() { return done; }

    public void setLocation(Location location) { this.location = location; }
    public void setPosition(int position) { this.position = position; }
    public void setDescription(String description) { this.description = description; }
    public void setSize(ShotSize size) { this.size = size; }
    public void setCameraBearing(Short cameraBearing) { this.cameraBearing = cameraBearing; }
    public void setPlannedTime(LocalTime plannedTime) { this.plannedTime = plannedTime; }
    public void setDone(boolean done) { this.done = done; }
}
