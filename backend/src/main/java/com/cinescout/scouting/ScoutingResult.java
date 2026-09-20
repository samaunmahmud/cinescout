package com.cinescout.scouting;

import com.cinescout.dto.LocationResponse;

import java.util.List;

/**
 * What one scouting run saved for a scene.
 *
 * @param added        the new candidate locations, best fit first
 * @param alreadySaved venues the search found again that were already saved for the scene; left
 *                     untouched, so a user's shortlist and notes survive a re-run
 * @param unassessed   venues found but dropped because the model could not assess them
 */
public record ScoutingResult(List<LocationResponse> added, int alreadySaved, int unassessed) {
}
