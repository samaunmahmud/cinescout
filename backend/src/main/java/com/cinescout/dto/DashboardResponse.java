package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The person's overview across the active productions they are on: the headline numbers, the next shoot days, the
 * best venues nobody has looked at yet, and the latest activity.
 */
public record DashboardResponse(Totals totals, List<ShootDay> upcoming, List<FreshFind> freshFinds, List<Happening> activity) {

    /**
     * @param lockedScenes scenes with a confirmed venue
     * @param venuesInPlay venues shortlisted, contacted or confirmed
     * @param followUps    sent emails gone unanswered long enough to chase
     */
    public record Totals(long productions, long scenes, long lockedScenes, long venuesInPlay, long followUps) {
    }

    /** A scene's shoot: when, where (its confirmed venue, if any), for which production. */
    public record ShootDay(UUID sceneId, Integer sceneNumber, String sceneTitle, UUID projectId, String projectTitle, LocalDate date,
                           LocalTime callTime, UUID venueId, String venueName, String venueImageUrl) {
    }

    /** A scouted venue still waiting to be looked at, best fit first. */
    public record FreshFind(UUID locationId, String name, String address, Integer fitScore, String imageUrl, LocationStatus status,
                            UUID sceneId, String sceneTitle, String projectTitle) {
    }

    /** A line of a production's activity log, with the production it is from. */
    public record Happening(ActivityResponse line, UUID projectId, String projectTitle) {
    }
}
