package com.cinescout.agreements;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a location release is filled in from. Every field but the production, the venue and the version may be null,
 * and is then left as a blank to fill in by hand. Only what the agreement needs: never notes, scores or the script.
 *
 * @param productionCompany the company making the production, when the user gives it
 * @param preparedBy        the crew member who generated it
 * @param quote             what the venue asked for, as the crew recorded it ("£1,200 a day")
 * @param callTime          access each day from (the scene's call time)
 * @param wrapTime          access each day until (the scene's wrap time; at or before the call it is the next morning)
 * @param crewSize          an estimate of the cast and crew on site
 */
public record AgreementFacts(
        String production,
        String productionCompany,
        String preparedBy,
        String venueName,
        String venueAddress,
        String contactName,
        String contactEmail,
        String contactPhone,
        String quote,
        LocalDate shootStart,
        LocalDate shootEnd,
        LocalTime callTime,
        LocalTime wrapTime,
        Integer crewSize,
        int version,
        LocalDate preparedOn
) {
}
