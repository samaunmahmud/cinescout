package com.cinescout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * What a letter to a venue's neighbours says that CineScout does not know (POST; the body and every field are optional).
 *
 * @param contactName who neighbours can call; the user's own name when null
 * @param parking     how the production will park; a plain promise to park legally when null
 * @param note        anything else neighbours should know (road closures, special effects, a drone)
 */
public record ResidentsLetterRequest(
        @Size(max = 200) String productionCompany,
        @Size(max = 120) String contactName,
        @Size(max = 40) String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 600) String parking,
        @Size(max = 1500) String note
) {
}
