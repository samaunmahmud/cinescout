package com.cinescout.domain;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "scenes")
public class Scene extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "scene_number")
    private Integer sceneNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_text", nullable = false)
    private String sourceText;

    @Column(name = "shoot_date_start")
    private LocalDate shootDateStart;

    @Column(name = "shoot_date_end")
    private LocalDate shootDateEnd;

    /** When the crew is called on a shoot day; null when not set. */
    @Column(name = "call_time")
    private LocalTime callTime;

    /** When the scene wraps; at or before {@code callTime} it is the next morning. Null when not set. */
    @Column(name = "wrap_time")
    private LocalTime wrapTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus = ParseStatus.PENDING;

    @Column(name = "setting_type")
    private String settingType;

    @Column(name = "visual_mood")
    private String visualMood;

    @Column(name = "lighting_needs")
    private String lightingNeeds;

    @Column(name = "time_of_day")
    private String timeOfDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "acoustic_sensitivity")
    private AcousticSensitivity acousticSensitivity;

    @Column(name = "estimated_crew_size")
    private Integer estimatedCrewSize;

    /** Full, unmodified parser output. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements_json")
    private JsonNode requirementsJson;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    protected Scene() {
    }

    public Scene(Project project, String title, String sourceText) {
        this.project = project;
        this.title = title;
        this.sourceText = sourceText;
    }

    /** Stores the parser's result and marks the scene as parsed. */
    public void applyRequirements(SceneRequirements requirements, JsonNode raw) {
        this.settingType = requirements.settingType();
        this.visualMood = requirements.visualMood();
        this.lightingNeeds = requirements.lightingNeeds();
        this.timeOfDay = requirements.timeOfDay();
        this.acousticSensitivity = requirements.acousticSensitivity();
        this.estimatedCrewSize = requirements.estimatedCastAndCrewSize();
        this.requirementsJson = raw;
        this.parseStatus = ParseStatus.PARSED;
        this.parsedAt = DatabaseTime.now();
    }

    /** Forgets the extracted requirements, e.g. because the script they came from has changed. */
    public void resetRequirements() {
        this.settingType = null;
        this.visualMood = null;
        this.lightingNeeds = null;
        this.timeOfDay = null;
        this.acousticSensitivity = null;
        this.estimatedCrewSize = null;
        this.requirementsJson = null;
        this.parsedAt = null;
        this.parseStatus = ParseStatus.PENDING;
    }

    public void markParseFailed() {
        this.parseStatus = ParseStatus.FAILED;
    }

    /** The extracted requirements, or null if the scene has not been parsed yet. */
    public SceneRequirements requirements() {
        if (parseStatus != ParseStatus.PARSED) {
            return null;
        }
        return new SceneRequirements(settingType, visualMood, lightingNeeds, timeOfDay,
                acousticSensitivity, estimatedCrewSize);
    }

    public Project getProject() { return project; }
    public Integer getSceneNumber() { return sceneNumber; }
    public String getTitle() { return title; }
    public String getSourceText() { return sourceText; }
    public LocalDate getShootDateStart() { return shootDateStart; }
    public LocalDate getShootDateEnd() { return shootDateEnd; }
    public LocalTime getCallTime() { return callTime; }
    public LocalTime getWrapTime() { return wrapTime; }
    public ParseStatus getParseStatus() { return parseStatus; }
    public JsonNode getRequirementsJson() { return requirementsJson; }
    public Instant getParsedAt() { return parsedAt; }

    public void setSceneNumber(Integer sceneNumber) { this.sceneNumber = sceneNumber; }
    public void setTitle(String title) { this.title = title; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public void setShootDateStart(LocalDate shootDateStart) { this.shootDateStart = shootDateStart; }
    public void setShootDateEnd(LocalDate shootDateEnd) { this.shootDateEnd = shootDateEnd; }
    public void setCallTime(LocalTime callTime) { this.callTime = callTime; }
    public void setWrapTime(LocalTime wrapTime) { this.wrapTime = wrapTime; }
}
