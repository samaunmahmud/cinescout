package com.cinescout.outreach;

import com.cinescout.domain.OutreachTone;

import java.time.LocalDate;

/**
 * Everything the model may know when it writes a follow-up to an unanswered email. Shorter still than
 * {@link OutreachBrief}: the chaser only reminds the recipient of the first email, by its subject and the day it went.
 * The script, private notes, fit scores, the first email's text and any reply never reach the model.
 *
 * @param senderName      who signs the email
 * @param production      the project title
 * @param venueName       the venue the first email was about
 * @param recipientName   who it was sent to; null when not known
 * @param originalSubject the first email's subject
 * @param sentOn          the day the first email went
 * @param tone            the first email's tone
 */
record FollowUpBrief(
        String senderName,
        String production,
        String venueName,
        String recipientName,
        String originalSubject,
        LocalDate sentOn,
        OutreachTone tone
) {
}
