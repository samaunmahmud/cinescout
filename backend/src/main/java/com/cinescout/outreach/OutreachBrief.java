package com.cinescout.outreach;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.OutreachTone;
import com.cinescout.domain.SceneRequirements;

import java.time.LocalDate;

/**
 * Everything the model may know when it writes to a venue owner. It is deliberately a short list:
 * the script, the project description, the user's private notes and the internal fit score never reach
 * the model, so they can never end up in an email to a stranger.
 *
 * @param senderName   who signs the email
 * @param production   the project title
 * @param shootStart   the scene's shoot window, either end may be null
 * @param requirements what the scene needs; null if the scene has not been parsed
 * @param bookingNote  what the booking route means in practice, as assessed from the venue's web page
 * @param venueNotes   an excerpt of the venue's web page (untrusted); null for a venue added by hand
 * @param senderNotes  free text from the sender that the email should work in
 */
record OutreachBrief(
        String senderName,
        String production,
        LocalDate shootStart,
        LocalDate shootEnd,
        SceneRequirements requirements,
        String venueName,
        String venueAddress,
        BookingFriction booking,
        String bookingNote,
        String venueNotes,
        String recipientName,
        OutreachTone tone,
        String senderNotes
) {
}
