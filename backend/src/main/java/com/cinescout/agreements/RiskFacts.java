package com.cinescout.agreements;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * What a draft risk assessment for a venue is written from: the scene, the tech recce answers and the cached logistics
 * report. Anything not recorded is null and is either left out or left as a blank to fill in on the walk-round.
 *
 * @param sceneHeading    the scene's heading ("EXT. FERRY DECK - NIGHT"), or its title
 * @param settingType     what kind of place the scene needs, as the parser read it
 * @param exterior        the heading says EXT (or INT/EXT)
 * @param night           a night scene, or a wrap after 22:00 or past midnight
 * @param stairsOrLift    the recce answer: GROUND_LEVEL, STAIRS, LIFT or STAIRS_AND_LIFT
 * @param phoneSignal     the recce answer: NONE, POOR, OK or STRONG
 * @param nearestHospital "Royal London Hospital, 1.2 km" from the logistics report; null without one
 * @param weatherWarnings the report's warnings for the shoot days, without repeats
 */
public record RiskFacts(
        String production,
        String venueName,
        String venueAddress,
        String sceneHeading,
        String settingType,
        boolean exterior,
        boolean night,
        LocalDate shootStart,
        LocalDate shootEnd,
        LocalTime callTime,
        LocalTime wrapTime,
        Integer crewSize,
        Integer sockets,
        Boolean threePhase,
        String powerNotes,
        Double ceilingHeightM,
        String loadInRoute,
        String stairsOrLift,
        Boolean stepFree,
        String phoneSignal,
        Boolean toilets,
        Boolean holdingSpace,
        String recceNotes,
        String nearestHospital,
        List<String> weatherWarnings,
        String preparedBy,
        LocalDate preparedOn
) {

    public RiskFacts {
        weatherWarnings = weatherWarnings == null ? List.of() : List.copyOf(weatherWarnings);
    }
}
