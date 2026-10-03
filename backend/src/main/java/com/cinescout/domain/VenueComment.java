package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A comment on a venue: by a member, or by a guest through a director link. A reply has a top-level comment as its
 * parent. Separate from the venue's private notes.
 */
@Entity
@Table(name = "venue_comments")
public class VenueComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private VenueComment parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @Column(name = "guest_name")
    private String guestName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_response_id", updatable = false)
    private DirectorResponse directorResponse;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<UUID> mentions = new ArrayList<>();

    protected VenueComment() {
    }

    /** A member's comment, or reply when {@code parent} is set. */
    public static VenueComment byMember(Location location, VenueComment parent, User author, String body, List<UUID> mentions) {
        VenueComment comment = new VenueComment();
        comment.location = location;
        comment.parent = parent;
        comment.author = author;
        comment.body = body;
        comment.mentions = new ArrayList<>(mentions);
        return comment;
    }

    /** The comment a guest left with their call through a director link. */
    public static VenueComment byGuest(Location location, DirectorResponse response, String guestName, String body) {
        VenueComment comment = new VenueComment();
        comment.location = location;
        comment.directorResponse = response;
        comment.guestName = guestName;
        comment.body = body;
        return comment;
    }

    public Location getLocation() {
        return location;
    }

    public VenueComment getParent() {
        return parent;
    }

    /** Null for a guest's comment, or once the author's account is deleted. */
    public User getAuthor() {
        return author;
    }

    public String getGuestName() {
        return guestName;
    }

    public boolean isByGuest() {
        return guestName != null;
    }

    public String getBody() {
        return body;
    }

    public List<UUID> getMentions() {
        return mentions == null ? List.of() : List.copyOf(mentions);
    }

    public void edit(String body, List<UUID> mentions) {
        this.body = body;
        this.mentions = new ArrayList<>(mentions);
    }

    /** A guest's comment follows their latest call: the name as they typed it last, and its comment. */
    public void followGuest(String guestName, String body) {
        this.guestName = guestName;
        this.body = body;
    }
}
