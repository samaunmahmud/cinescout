package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;

/** A venue the search found, together with the model's assessment of it against the scene. */
public record ScoutedVenue(SearchResult source, LocationAssessment assessment) {
}
