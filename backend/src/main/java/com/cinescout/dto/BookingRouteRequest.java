package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;

/**
 * Who has to say yes to filming at a venue, as the crew knows it (PUT): PUBLIC (a public space: the council), COMMERCIAL
 * (a business) or PRIVATE (a private owner). Null clears it. Replaces what scouting assessed.
 */
public record BookingRouteRequest(BookingFriction bookingFriction) {
}
