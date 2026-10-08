package com.cinescout.dto;

import com.cinescout.domain.Location;
import com.cinescout.domain.Shot;
import com.cinescout.domain.ShotSize;
import com.cinescout.logistics.solar.ShotLight;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A shot, numbered in shooting order, with the venue it is at and how the sun lights it then. {@code sun} is null
 * when that cannot be worked out, and {@code sunMissing} says what is missing: NO_VENUE, NO_PIN, NO_DATE, NO_TIME or
 * NO_ZONE (the venue's logistics have not been worked out, so its time zone is not known).
 */
public record ShotResponse(
        UUID id,
        UUID sceneId,
        int number,
        String description,
        ShotSize size,
        Integer cameraBearing,
        LocalTime plannedTime,
        boolean done,
        UUID locationId,
        String venueName,
        boolean venueConfirmed,
        ShotLight sun,
        String sunMissing
) {

    public static ShotResponse from(Shot shot, int number, Location venue, boolean confirmed, ShotLight sun, String sunMissing) {
        return new ShotResponse(shot.getId(), shot.getScene().getId(), number, shot.getDescription(), shot.getSize(),
                shot.getCameraBearing() == null ? null : shot.getCameraBearing().intValue(), shot.getPlannedTime(), shot.isDone(),
                venue == null ? null : venue.getId(), venue == null ? null : venue.getName(), confirmed, sun, sunMissing);
    }
}
