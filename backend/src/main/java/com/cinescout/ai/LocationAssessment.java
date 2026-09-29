package com.cinescout.ai;

import com.cinescout.domain.BookingFriction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * The LLM's feasibility and risk assessment of one candidate venue against a
 * scene's requirements. Like {@code SceneRequirements}, this record is the
 * contract for the model's structured JSON output; the LLM client validates it
 * before anything is persisted, since model output is untrusted.
 *
 * @param singleVenue        whether the page is about one specific venue (its own site, or one listing on a
 *                           booking site). False for directories, "best rooftops in town" lists and articles
 *                           naming several places: those are dropped, since a page of ten venues is not a
 *                           location anyone can book or put on a map
 * @param fitScore           how well the venue suits the scene, 0 (unusable) to 100 (ideal)
 * @param fitReason          one or two sentences explaining the score
 * @param bookingFriction    who has to say yes: public space, commercial venue or private owner
 * @param frictionNote       what that means in practice (permits, hire process, lead time); optional
 * @param footprintWarnings  short warnings about the crew's footprint (access, noise curfews,
 *                           power, parking); never null, empty when there are none
 * @param venueName          the venue's own name, cleaner than the page title it was found under; optional
 * @param address            the venue's street address when the excerpt states it, used to find it on the
 *                           map; optional, never guessed
 */
public record LocationAssessment(
        @NotNull Boolean singleVenue,
        @NotNull @Min(0) @Max(100) Integer fitScore,
        @NotBlank String fitReason,
        @NotNull BookingFriction bookingFriction,
        String frictionNote,
        List<@NotBlank String> footprintWarnings,
        String venueName,
        String address
) {

    /**
     * Models often omit an empty array or pad it with nulls, so both mean "no warnings"; a blank name or
     * address means none.
     */
    public LocationAssessment {
        footprintWarnings = footprintWarnings == null
                ? List.of()
                : footprintWarnings.stream().filter(Objects::nonNull).toList();
        venueName = blankToNull(venueName);
        address = blankToNull(address);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
