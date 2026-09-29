package com.cinescout.scouting;

import java.util.List;

/**
 * The result of one scouting run.
 *
 * @param venues     assessed venues, best fit first (ties keep the search's own ranking)
 * @param unassessed how many found venues were dropped because the model could not produce a usable
 *                   assessment; they are not guessed at. Zero when everything was assessed
 * @param notVenues  how many search results the model found were not about one venue (a directory, a list, an
 *                   article) and were dropped
 */
public record ScoutingOutcome(List<ScoutedVenue> venues, int unassessed, int notVenues) {

    public ScoutingOutcome {
        venues = List.copyOf(venues);
    }
}
