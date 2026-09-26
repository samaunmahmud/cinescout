package com.cinescout.video;

import java.time.Instant;
import java.util.List;

/**
 * Videos about a venue.
 *
 * @param query     what was searched for: the venue's name and where it is
 * @param fetchedAt when the search ran (results are cached)
 * @param videos    best match first; may be empty
 */
public record LocationVideos(String query, Instant fetchedAt, List<Video> videos) {
}
