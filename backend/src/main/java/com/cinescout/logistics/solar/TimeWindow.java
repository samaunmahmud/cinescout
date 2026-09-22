package com.cinescout.logistics.solar;

import java.time.OffsetDateTime;

/** A stretch of time on a shoot day, in the location's local time. */
public record TimeWindow(OffsetDateTime start, OffsetDateTime end) {
}
