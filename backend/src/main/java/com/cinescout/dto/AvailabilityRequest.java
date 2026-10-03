package com.cinescout.dto;

import com.cinescout.domain.AvailabilityState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Sets a venue's state on a day, or on every day of a run of them (PUT; each day is replaced as a whole).
 *
 * @param from          the first day
 * @param to            the last day, at most {@value #MAX_DAYS} days on; null for {@code from} alone
 * @param holdExpiresOn when a pencil or hold lapses unless renewed; only for PENCILLED and HELD
 * @param note          e.g. "Held by Jo, call back Friday"
 */
public record AvailabilityRequest(
        @NotNull LocalDate from,
        LocalDate to,
        @NotNull AvailabilityState state,
        LocalDate holdExpiresOn,
        @Size(max = 500) String note
) {

    public static final int MAX_DAYS = 62;

    public LocalDate lastDay() {
        return to == null ? from : to;
    }

    @JsonIgnore
    @AssertTrue(message = "to must be on or after from, and at most 62 days on")
    public boolean isRangeValid() {
        return from == null || to == null || (!to.isBefore(from) && ChronoUnit.DAYS.between(from, to) < MAX_DAYS);
    }

    @JsonIgnore
    @AssertTrue(message = "only a pencil or a hold can have an expiry date")
    public boolean isExpiryValid() {
        return holdExpiresOn == null || state == null || state.canExpire();
    }
}
