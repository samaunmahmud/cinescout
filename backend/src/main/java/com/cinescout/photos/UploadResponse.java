package com.cinescout.photos;

/**
 * A photo just taken up. {@code suggestPin} when the venue has no position yet and the photo says where it was
 * taken: the web app offers to place the pin there, and the user decides.
 */
public record UploadResponse(PhotoResponse photo, boolean suggestPin) {
}
