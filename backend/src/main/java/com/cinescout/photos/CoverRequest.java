package com.cinescout.photos;

import java.util.UUID;

/** Which of the venue's photos its polaroid shows; null goes back to the picture from its web page. */
public record CoverRequest(UUID photoId) {
}
