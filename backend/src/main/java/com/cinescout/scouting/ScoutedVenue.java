package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;

/** A venue the search found, together with the model's assessment of it against the scene. */
public record ScoutedVenue(SearchResult source, LocationAssessment assessment) {

    /** The same venue and assessment, at {@code address}. */
    ScoutedVenue withAddress(String address) {
        LocationAssessment a = assessment;
        return new ScoutedVenue(source, new LocationAssessment(a.singleVenue(), a.fitScore(), a.fitReason(), a.bookingFriction(),
                a.frictionNote(), a.footprintWarnings(), a.venueName(), address));
    }
}
