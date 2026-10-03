package com.cinescout.dto;

import com.cinescout.domain.LocationAgreement;

import java.time.Instant;
import java.util.UUID;

/**
 * One version of a venue's location release. The PDF is at {@code GET /api/agreements/{id}/file}.
 *
 * @param createdByName who generated it; null once that account is gone
 */
public record AgreementResponse(UUID id, UUID locationId, int version, int sizeBytes, String createdByName, Instant createdAt) {

    public static AgreementResponse from(LocationAgreement agreement) {
        return new AgreementResponse(agreement.getId(), agreement.getLocation().getId(), agreement.getVersion(), agreement.getSizeBytes(),
                agreement.getCreatedBy() == null ? null : agreement.getCreatedBy().getDisplayName(), agreement.getCreatedAt());
    }
}
