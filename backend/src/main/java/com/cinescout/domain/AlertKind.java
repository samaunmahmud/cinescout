package com.cinescout.domain;

/** What an alert is about. */
public enum AlertKind {
    /** Rain or wind over the project's thresholds on a shoot day at a confirmed venue. */
    WEATHER,
    /** An outreach email has waited longer than the project's follow-up days without a reply. */
    FOLLOW_UP
}
