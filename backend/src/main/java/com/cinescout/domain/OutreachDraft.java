package com.cinescout.domain;

import com.cinescout.security.SecretTokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outreach_drafts")
public class OutreachDraft extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutreachTone tone = OutreachTone.PROFESSIONAL;

    /** Model id that wrote the first draft. */
    @Column(name = "generated_by")
    private String generatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutreachStatus status = OutreachStatus.DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** The secret part of this draft's own reply address; set when the draft is made, never changed. */
    @Column(name = "reply_token", nullable = false, updatable = false)
    private String replyToken = SecretTokens.newHexToken();

    /** When the follow-up job found this email unanswered for too long; null while it is not waiting on a follow-up. */
    @Column(name = "follow_up_flagged_at")
    private Instant followUpFlaggedAt;

    /** The earlier email this one chases; null for a first email. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_of", updatable = false)
    private OutreachDraft followUpOf;

    protected OutreachDraft() {
    }

    public OutreachDraft(Location location, User createdBy, String subject, String body, OutreachTone tone) {
        this.location = location;
        this.createdBy = createdBy;
        this.subject = subject;
        this.body = body;
        this.tone = tone;
    }

    public Location getLocation() { return location; }
    public User getCreatedBy() { return createdBy; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public OutreachTone getTone() { return tone; }
    public String getGeneratedBy() { return generatedBy; }
    public OutreachStatus getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }

    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setTone(OutreachTone tone) { this.tone = tone; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public void setStatus(OutreachStatus status) { this.status = status; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getReplyToken() { return replyToken; }
    public Instant getFollowUpFlaggedAt() { return followUpFlaggedAt; }
    public OutreachDraft getFollowUpOf() { return followUpOf; }

    /** Makes this draft the follow-up of {@code earlier}, which then no longer waits on one. */
    public void followUp(OutreachDraft earlier) {
        this.followUpOf = earlier;
        earlier.followUpFlaggedAt = null;
    }

    /**
     * Moves the draft to {@code next} and keeps {@code sentAt} consistent with it: stamped when the
     * draft first leaves {@code DRAFT}, cleared when it goes back. A draft that is no longer SENT waits on no
     * follow-up. The user reports what happened; nothing here sends an email.
     */
    public void changeStatus(OutreachStatus next) {
        this.status = next;
        if (next != OutreachStatus.SENT) {
            this.followUpFlaggedAt = null;
        }
        if (next == OutreachStatus.DRAFT) {
            this.sentAt = null;
        } else if (this.sentAt == null) {
            this.sentAt = DatabaseTime.now();
        }
    }
}
