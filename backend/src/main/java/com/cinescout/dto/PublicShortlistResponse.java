package com.cinescout.dto;

/**
 * What a director link shows: the production, the scene when the link is for one, and a page of its shortlisted
 * venues. {@code sharedBy} is the name of the member who made the link.
 */
public record PublicShortlistResponse(
        String projectTitle,
        String sceneTitle,
        String sharedBy,
        boolean showPrivate,
        PageResponse<ShortlistVenueResponse> venues
) {
}
