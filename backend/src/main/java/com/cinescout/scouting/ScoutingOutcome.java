package com.cinescout.scouting;

import java.util.List;

/**
 * The result of one scouting run.
 *
 * @param venues     assessed venues, best fit first (ties keep the search's own ranking)
 * @param unassessed how many found venues were dropped because the model could not produce a usable
 *                   assessment; they are not guessed at. Zero when everything was assessed
 */
public record ScoutingOutcome(List<ScoutedVenue> venues, int unassessed) {

    public ScoutingOutcome {
        venues = List.copyOf(venues);
    }
}
