package com.cinescout.dto;

/** A project's shared call sheet link: the web app's page for it is {@code /call-sheet/{token}}. */
public record CallSheetLinkResponse(String token) {
}
