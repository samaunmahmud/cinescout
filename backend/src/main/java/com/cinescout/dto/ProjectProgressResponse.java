package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;

import java.util.Map;

/**
 * How far a project's location scouting has come.
 *
 * @param scenes              all the project's scenes
 * @param scenesWithLocations scenes that have at least one candidate location
 * @param scenesConfirmed     scenes that have a confirmed location
 * @param locations           all candidate locations, over all scenes
 * @param locationsByStatus   the same, by status; every status is present, 0 when none
 */
public record ProjectProgressResponse(
        long scenes,
        long scenesWithLocations,
        long scenesConfirmed,
        long locations,
        Map<LocationStatus, Long> locationsByStatus
) {
}
