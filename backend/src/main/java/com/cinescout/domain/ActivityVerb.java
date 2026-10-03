package com.cinescout.domain;

/** What happened, each under the {@link ActivityKind} the log's filter shows it with. */
public enum ActivityVerb {
    VENUE_STATUS_CHANGED(ActivityKind.VENUE),
    DIRECTOR_CALLED(ActivityKind.VENUE),
    SCOUTED(ActivityKind.SCOUTING),
    OUTREACH_STATUS_CHANGED(ActivityKind.OUTREACH),
    MEMBER_JOINED(ActivityKind.CREW),
    MEMBER_LEFT(ActivityKind.CREW),
    MEMBER_REMOVED(ActivityKind.CREW),
    ROLE_CHANGED(ActivityKind.CREW),
    OWNERSHIP_TRANSFERRED(ActivityKind.CREW),
    SHOOT_DATES_CHANGED(ActivityKind.SCHEDULE),
    COMMENTED(ActivityKind.COMMENT);

    private final ActivityKind kind;

    ActivityVerb(ActivityKind kind) {
        this.kind = kind;
    }

    public ActivityKind kind() {
        return kind;
    }
}
