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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "locations")
public class Location extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false, updatable = false)
    private Scene scene;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_provider")
    private String sourceProvider;

    @Column(name = "source_excerpt")
    private String sourceExcerpt;

    @Column(name = "fit_reason")
    private String fitReason;

    /** 0-100. SMALLINT in the database, so Short here. */
    @Column(name = "fit_score")
    private Short fitScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_friction")
    private BookingFriction bookingFriction;

    @Column(name = "friction_note")
    private String frictionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footprint_warnings", nullable = false)
    private List<String> footprintWarnings = new ArrayList<>();

    /** Cached Module B output: solar windows, weather, noise risks, nearby services. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logistics_json")
    private JsonNode logisticsJson;

    @Column(name = "logistics_fetched_at")
    private Instant logisticsFetchedAt;

    /** Cached video search results for the venue, and the query they answer. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "videos_json")
    private JsonNode videosJson;

    @Column(name = "videos_query")
    private String videosQuery;

    @Column(name = "videos_fetched_at")
    private Instant videosFetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationStatus status = LocationStatus.SUGGESTED;

    private String notes;

    /** Who to talk to at the venue, as the user entered it. */
    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    /** What the venue asks for the shoot, as the user noted it. */
    private String quote;

    protected Location() {
    }

    public Location(Scene scene, String name) {
        this.scene = scene;
        this.name = name;
    }

    /** Moves the location, dropping the cached logistics: they were worked out for the old spot. */
    public void relocate(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.logisticsJson = null;
        this.logisticsFetchedAt = null;
    }

    public void cacheLogistics(JsonNode logistics) {
        this.logisticsJson = logistics;
        this.logisticsFetchedAt = DatabaseTime.now();
    }

    public void cacheVideos(JsonNode videos, String query, Instant fetchedAt) {
        this.videosJson = videos;
        this.videosQuery = query;
        this.videosFetchedAt = fetchedAt;
    }

    public Scene getScene() { return scene; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceProvider() { return sourceProvider; }
    public String getSourceExcerpt() { return sourceExcerpt; }
    public String getFitReason() { return fitReason; }
    public Short getFitScore() { return fitScore; }
    public BookingFriction getBookingFriction() { return bookingFriction; }
    public String getFrictionNote() { return frictionNote; }
    public List<String> getFootprintWarnings() { return footprintWarnings; }
    public JsonNode getLogisticsJson() { return logisticsJson; }
    public Instant getLogisticsFetchedAt() { return logisticsFetchedAt; }
    public JsonNode getVideosJson() { return videosJson; }
    public String getVideosQuery() { return videosQuery; }
    public Instant getVideosFetchedAt() { return videosFetchedAt; }
    public LocationStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getQuote() { return quote; }

    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setSourceProvider(String sourceProvider) { this.sourceProvider = sourceProvider; }
    public void setSourceExcerpt(String sourceExcerpt) { this.sourceExcerpt = sourceExcerpt; }
    public void setFitReason(String fitReason) { this.fitReason = fitReason; }
    public void setFitScore(Short fitScore) { this.fitScore = fitScore; }
    public void setBookingFriction(BookingFriction bookingFriction) { this.bookingFriction = bookingFriction; }
    public void setFrictionNote(String frictionNote) { this.frictionNote = frictionNote; }
    public void setFootprintWarnings(List<String> footprintWarnings) { this.footprintWarnings = footprintWarnings; }
    public void setStatus(LocationStatus status) { this.status = status; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public void setQuote(String quote) { this.quote = quote; }
}
