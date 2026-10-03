package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/** One generated version of a venue's location release; the PDF itself is in the file store. Never changed. */
@Entity
@Table(name = "location_agreements")
public class LocationAgreement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected LocationAgreement() {
    }

    /** The id is chosen before the file is stored, as its key is made from it. */
    public LocationAgreement(UUID id, Location location, int version, String storageKey, int sizeBytes, User createdBy) {
        this.id = id;
        this.location = location;
        this.version = version;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public Location getLocation() { return location; }
    public int getVersion() { return version; }
    public String getStorageKey() { return storageKey; }
    public int getSizeBytes() { return sizeBytes; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
