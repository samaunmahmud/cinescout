package com.cinescout.web;

import com.cinescout.calendar.CalendarFeedService;
import com.cinescout.dto.CalendarLinkResponse;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@Tag(name = "Calendar", description = "A secret iCalendar feed of the shoot days across the projects you are on.")
class CalendarController {

    static final MediaType CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarFeedService calendars;

    CalendarController(CalendarFeedService calendars) {
        this.calendars = calendars;
    }

    @Operation(summary = "Get your calendar feed link", description = "404 while the feed is off. The feed is /api/public/calendars/{token}.ics.")
    @GetMapping("/api/account/calendar-link")
    Mono<CalendarLinkResponse> link(@AuthenticationPrincipal AuthenticatedUser user) {
        return calendars.link(user.id());
    }

    @Operation(summary = "Turn the calendar feed on, or give it a new link", description = "A new link replaces the old one, which stops working.")
    @PostMapping("/api/account/calendar-link")
    Mono<CalendarLinkResponse> create(@AuthenticationPrincipal AuthenticatedUser user) {
        return calendars.create(user.id());
    }

    @Operation(summary = "Turn the calendar feed off")
    @DeleteMapping("/api/account/calendar-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> turnOff(@AuthenticationPrincipal AuthenticatedUser user) {
        return calendars.turnOff(user.id());
    }

    @Operation(summary = "The calendar feed (no login: the token is the permission)",
            description = "An iCalendar (RFC 5545) document of the shoot days of every active project the link's owner is on.")
    @GetMapping("/api/public/calendars/{token}.ics")
    Mono<ResponseEntity<String>> feed(@PathVariable String token, ServerHttpRequest request) {
        return calendars.feed(token, appUrl(request)).map(ics -> ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cinescout.ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Robots-Tag", "noindex")
                .header("Referrer-Policy", "no-referrer")
                .body(ics));
    }

    /**
     * Where the web app is, from the request that reached the feed (the API and the app share an origin), for the links
     * to call sheets; empty when the request carries no host, which leaves the links relative.
     */
    private static String appUrl(ServerHttpRequest request) {
        URI uri = request.getURI();
        String host = uri.getRawAuthority() != null ? uri.getRawAuthority() : request.getHeaders().getFirst(HttpHeaders.HOST);
        return host == null ? "" : (uri.getScheme() != null ? uri.getScheme() : "http") + "://" + host;
    }
}
