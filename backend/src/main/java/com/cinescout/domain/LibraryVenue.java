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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A venue in a user's own library: the facts about a place that hold whatever scene it is for, with the user's
 * tags and notes. Copied from a scouted venue, and copied again into any scene without a new search or assessment.
 */
@Entity
@Table(name = "library_venues")
public class LibraryVenue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "source_location_id", updatable = false)
    private UUID sourceLocationId;

    @Column(nullable = false)
    private String name;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_friction")
    private BookingFriction bookingFriction;

    @Column(name = "friction_note")
    private String frictionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footprint_warnings", nullable = false)
    private List<String> footprintWarnings = new ArrayList<>();

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    private String notes;

    protected LibraryVenue() {
    }

    /** The scene-independent facts of a scouted venue, for {@code owner}'s library. */
    public static LibraryVenue from(Location location, User owner) {
        LibraryVenue venue = new LibraryVenue();
        venue.owner = owner;
        venue.sourceLocationId = location.getId();
        venue.name = location.getName();
        venue.address = location.getAddress();
        venue.latitude = location.getLatitude();
        venue.longitude = location.getLongitude();
        venue.sourceUrl = location.getSourceUrl();
        venue.imageUrl = location.getImageUrl();
        venue.bookingFriction = location.getBookingFriction();
        venue.frictionNote = location.getFrictionNote();
        venue.footprintWarnings = location.getFootprintWarnings() == null ? new ArrayList<>() : new ArrayList<>(location.getFootprintWarnings());
        venue.contactName = location.getContactName();
        venue.contactEmail = location.getContactEmail();
        venue.contactPhone = location.getContactPhone();
        return venue;
    }

    /** A copy for {@code scene}, as a venue added without scouting: no fit score, status or crew notes. */
    public Location toLocation(Scene scene) {
        Location location = new Location(scene, name);
        location.setAddress(address);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setSourceUrl(sourceUrl);
        location.setBookingFriction(bookingFriction);
        location.setFrictionNote(frictionNote);
        location.setFootprintWarnings(new ArrayList<>(footprintWarnings == null ? List.of() : footprintWarnings));
        location.setContactName(contactName);
        location.setContactEmail(contactEmail);
        location.setContactPhone(contactPhone);
        return location;
    }

    public User getOwner() { return owner; }
    public UUID getSourceLocationId() { return sourceLocationId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getSourceUrl() { return sourceUrl; }
    public String getImageUrl() { return imageUrl; }
    public BookingFriction getBookingFriction() { return bookingFriction; }
    public String getFrictionNote() { return frictionNote; }
    public List<String> getFootprintWarnings() { return footprintWarnings == null ? List.of() : List.copyOf(footprintWarnings); }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public List<String> getTags() { return tags == null ? List.of() : List.copyOf(tags); }
    public String getNotes() { return notes; }

    public void setName(String name) { this.name = name; }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); }
    public void setNotes(String notes) { this.notes = notes; }
}
