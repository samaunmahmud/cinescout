package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/** A reply to an outreach email: one that came in by mail ({@code INBOUND}) or one a member pasted in ({@code MANUAL}). */
@Entity
@Table(name = "outreach_replies")
public class OutreachReply {

    public enum Source { INBOUND, MANUAL }

    /** The most of a reply's text that is kept. */
    public static final int MAX_TEXT = 20 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false, updatable = false)
    private OutreachDraft draft;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Source source;

    @Column(name = "from_address", updatable = false)
    private String fromAddress;

    @Column(name = "from_name", updatable = false)
    private String fromName;

    @Column(updatable = false)
    private String subject;

    @Column(name = "body_text", updatable = false)
    private String bodyText;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", updatable = false)
    private User recordedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected OutreachReply() {
    }

    public OutreachReply(OutreachDraft draft, Source source, String fromAddress, String fromName, String subject, String text,
                         Instant receivedAt, User recordedBy) {
        this.draft = draft;
        this.source = source;
        this.fromAddress = cap(fromAddress, 320);
        this.fromName = cap(fromName, 200);
        this.subject = cap(subject, 500);
        this.bodyText = cap(text, MAX_TEXT);
        this.receivedAt = receivedAt;
        this.recordedBy = recordedBy;
    }

    private static String cap(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    public UUID getId() { return id; }
    public OutreachDraft getDraft() { return draft; }
    public Source getSource() { return source; }
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public String getSubject() { return subject; }
    public String getBodyText() { return bodyText; }
    public Instant getReceivedAt() { return receivedAt; }
    public User getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
