package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A backup venue for a scene, one of the scene's own candidates, with when to switch to it ("if rain > 60%"). */
@Entity
@Table(name = "scene_covers")
public class SceneCover extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false, updatable = false)
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    /** Free text; null when no trigger was given. */
    @Column(name = "trigger_text")
    private String trigger;

    /** Null once that account is gone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    protected SceneCover() {
    }

    public SceneCover(Scene scene, Location location, String trigger, User addedBy) {
        this.scene = scene;
        this.location = location;
        this.trigger = trigger;
        this.addedBy = addedBy;
    }

    public Scene getScene() { return scene; }
    public Location getLocation() { return location; }
    public String getTrigger() { return trigger; }
    public User getAddedBy() { return addedBy; }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }
}
