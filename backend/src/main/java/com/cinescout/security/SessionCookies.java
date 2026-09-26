package com.cinescout.security;

import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

/**
 * The session cookie. It is {@code HttpOnly} (scripts cannot read it), {@code SameSite=Strict} and scoped to
 * {@code /api}; it is {@code Secure} when the request came over HTTPS, or as configured.
 *
 * <p>A browser attaches cookies on its own, so a cookie login needs CSRF protection. Here the cookie only
 * counts on a request that also carries {@code X-Requested-With: XMLHttpRequest}: the web app always sends
 * it, and another site cannot add a custom header to a request here without a CORS grant this API never
 * gives. SameSite=Strict is a second line of defence.
 */
public final class SessionCookies {

    public static final String NAME = "cinescout_session";
    private static final String PATH = "/api";

    private final Boolean secure;

    /** @param secure whether the cookie is {@code Secure}; null decides per request, by its scheme */
    public SessionCookies(Boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(ServerWebExchange exchange, String token, Duration ttl) {
        return base(exchange, token).maxAge(ttl).build();
    }

    public ResponseCookie clear(ServerWebExchange exchange) {
        return base(exchange, "").maxAge(Duration.ZERO).build();
    }

    /** The session token, if the request carries the cookie and the script header that makes it count. */
    public static String token(ServerHttpRequest request) {
        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeaders().getFirst("X-Requested-With"))) {
            return null;
        }
        HttpCookie cookie = request.getCookies().getFirst(NAME);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    private ResponseCookie.ResponseCookieBuilder base(ServerWebExchange exchange, String value) {
        boolean overHttps = "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure != null ? secure : overHttps)
                .sameSite("Strict")
                .path(PATH);
    }
}
