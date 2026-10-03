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
 * @param unsuitable how many venues the model scored 0, unusable for the scene (e.g. outside the search area), and
 *                   were dropped
 * @param filtered   how many the run's filters left out, by reason
 */
public record ScoutingOutcome(List<ScoutedVenue> venues, int unassessed, int notVenues, int unsuitable, FilteredOut filtered) {

    public ScoutingOutcome {
        venues = List.copyOf(venues);
        filtered = filtered == null ? FilteredOut.NONE : filtered;
    }

    public ScoutingOutcome(List<ScoutedVenue> venues, int unassessed, int notVenues, int unsuitable) {
        this(venues, unassessed, notVenues, unsuitable, FilteredOut.NONE);
    }
}
