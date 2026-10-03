package com.cinescout.photos;

/** An upload that is not a photo this can take: not a JPEG or PNG, damaged, or far too large. */
public class UnreadableImageException extends RuntimeException {

    public UnreadableImageException(String message) {
        super(message);
    }
}
