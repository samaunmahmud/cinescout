package com.cinescout.dto;

import java.util.List;

/**
 * What plan B did: the backup venue's days with the new pencil, and the email drafted to its owner, or why there is
 * none ({@code draftProblem}: the AI is not set up, busy, or the allowance is spent), in which case write it later.
 */
public record PlanBResponse(List<AvailabilityResponse> availability, OutreachDraftResponse draft, String draftProblem) {
}
