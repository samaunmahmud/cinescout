package com.cinescout.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachDraftTest {

    private static OutreachDraft draft() {
        return new OutreachDraft(null, null, "Subject", "Body", OutreachTone.PROFESSIONAL);
    }

    @Test
    void aNewDraftIsADraftThatWasNotSent() {
        assertThat(draft().getStatus()).isEqualTo(OutreachStatus.DRAFT);
        assertThat(draft().getSentAt()).isNull();
    }

    @Test
    void leavingDraftStampsSentAtOnce() {
        OutreachDraft draft = draft();
        Instant before = Instant.now();

        draft.changeStatus(OutreachStatus.SENT);
        Instant stamped = draft.getSentAt();
        draft.changeStatus(OutreachStatus.REPLIED);

        assertThat(stamped).isBetween(before, Instant.now());
        assertThat(draft.getSentAt()).isEqualTo(stamped);
        assertThat(draft.getStatus()).isEqualTo(OutreachStatus.REPLIED);
    }

    @Test
    void goingStraightToRepliedStillRecordsWhenItLeftDraft() {
        OutreachDraft draft = draft();

        draft.changeStatus(OutreachStatus.REPLIED);

        assertThat(draft.getSentAt()).isNotNull();
    }

    @Test
    void returningToDraftClearsSentAt() {
        OutreachDraft draft = draft();
        draft.changeStatus(OutreachStatus.SENT);

        draft.changeStatus(OutreachStatus.DRAFT);

        assertThat(draft.getStatus()).isEqualTo(OutreachStatus.DRAFT);
        assertThat(draft.getSentAt()).isNull();
    }
}
