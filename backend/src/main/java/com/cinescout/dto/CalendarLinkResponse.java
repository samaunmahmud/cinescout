package com.cinescout.dto;

/** The signed-in person's calendar feed link: the feed is {@code /api/public/calendars/{token}.ics}. */
public record CalendarLinkResponse(String token) {
}
