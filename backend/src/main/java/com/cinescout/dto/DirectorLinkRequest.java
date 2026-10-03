package com.cinescout.dto;

/**
 * Options for a new director link. {@code showPrivate} also shows the private notes, the quote and the reason for
 * each venue's fit score; only the project's owner can turn it on. Null means false.
 */
public record DirectorLinkRequest(Boolean showPrivate) {

    public boolean privateShown() {
        return Boolean.TRUE.equals(showPrivate);
    }
}
