package com.cinescout.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Company moves: on each shoot day with more than one venue, the drive from one to the next in the day's order (by
 * call time, then script order).
 *
 * @param warnAfterMinutes a move longer than this is flagged {@code long}
 * @param attribution      the routing data's credit, to show with the times
 */
public record MovesResponse(List<Day> days, int warnAfterMinutes, String attribution) {

    public record Day(LocalDate date, List<Move> moves) {
    }

    public enum Status {
        /** Worked out: {@code minutes} and {@code kilometres} are set. */
        OK,
        /** One of the venues has no position on the map. */
        UNPLACED,
        /** No road between them. */
        NO_ROUTE,
        /** Not worked out yet (too many at once, or the shared call sheet, which never asks the router); load again later. */
        PENDING,
        /** The routing service could not be reached. */
        UNAVAILABLE
    }

    /**
     * @param minutes    the drive without traffic, rounded up; null unless OK
     * @param kilometres by road, to one decimal; null unless OK
     * @param tooLong    longer than the project's warning threshold
     * @param text       the move in words: "Starlite Diner to Neon Spoon Cafe: 21 min, 7.8 km by road"
     */
    public record Move(UUID fromLocationId, String fromName, UUID fromSceneId, UUID toLocationId, String toName, UUID toSceneId,
                       Status status, Integer minutes, Double kilometres, boolean tooLong, String text) {
    }
}
