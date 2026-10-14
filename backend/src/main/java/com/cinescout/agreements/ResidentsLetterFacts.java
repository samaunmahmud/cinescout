package com.cinescout.agreements;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a letter to the neighbours of a venue is written from. Only the production, the venue and the date are always
 * there; anything else left null is either left out of the letter or worded round. Never notes, scores, the quote or the
 * venue owner's details: the letter goes through strangers' doors.
 *
 * @param productionCompany who is filming, when the user gives it
 * @param night             some of the filming is at night (a night scene, or a wrap after 22:00 or past midnight)
 * @param contactName       who neighbours can call before and during the shoot
 * @param parking           how the production will park, in the user's words
 * @param note              anything else neighbours should know, in the user's words
 */
public record ResidentsLetterFacts(
        String production,
        String productionCompany,
        String venueName,
        String venueAddress,
        LocalDate shootStart,
        LocalDate shootEnd,
        LocalTime callTime,
        LocalTime wrapTime,
        Integer crewSize,
        boolean night,
        String contactName,
        String contactPhone,
        String contactEmail,
        String parking,
        String note,
        LocalDate preparedOn
) {
}
