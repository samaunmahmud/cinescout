package com.cinescout.dto;

/** A tag in the user's library and how many venues carry it. */
public record TagCountResponse(String tag, long venues) {
}
