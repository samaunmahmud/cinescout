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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A photo from a recce of a venue, stored without its metadata; only its GPS position is kept, here. */
@Entity
@Table(name = "location_photos")
public class LocationPhoto {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", updatable = false)
    private User uploadedBy;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "thumb_key", nullable = false, updatable = false)
    private String thumbKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "gps_latitude", updatable = false)
    private BigDecimal gpsLatitude;

    @Column(name = "gps_longitude", updatable = false)
    private BigDecimal gpsLongitude;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected LocationPhoto() {
    }

    /** The id is chosen before the upload is stored, as the file's key is made from it. */
    public LocationPhoto(UUID id, Location location, User uploadedBy, String storageKey, String thumbKey, String contentType,
                         int width, int height, int sizeBytes, BigDecimal gpsLatitude, BigDecimal gpsLongitude) {
        this.id = id;
        this.location = location;
        this.uploadedBy = uploadedBy;
        this.storageKey = storageKey;
        this.thumbKey = thumbKey;
        this.contentType = contentType;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.gpsLatitude = gpsLatitude;
        this.gpsLongitude = gpsLongitude;
    }

    public UUID getId() { return id; }
    public Location getLocation() { return location; }
    public User getUploadedBy() { return uploadedBy; }
    public String getStorageKey() { return storageKey; }
    public String getThumbKey() { return thumbKey; }
    public String getContentType() { return contentType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSizeBytes() { return sizeBytes; }
    public BigDecimal getGpsLatitude() { return gpsLatitude; }
    public BigDecimal getGpsLongitude() { return gpsLongitude; }
    public Instant getCreatedAt() { return createdAt; }
}
