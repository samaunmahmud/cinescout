package com.cinescout.dto;

import jakarta.validation.constraints.Size;

/**
 * What a location release needs that CineScout does not know (POST; the body is optional).
 *
 * @param productionCompany the company making the production; left blank on the form when null
 */
public record AgreementRequest(@Size(max = 200) String productionCompany) {
}
