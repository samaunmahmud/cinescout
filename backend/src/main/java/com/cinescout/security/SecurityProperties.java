package com.cinescout.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param registrationOpen whether anyone may create an account. Turn off ({@code cinescout.security.registration-open=false})
 *                         once the accounts you want exist, for a deployment that is not meant to be public
 * @param sessionTtl       how long a web app login lasts
 * @param cookieSecure     whether the session cookie is {@code Secure}. Unset, it is when the request came over
 *                         HTTPS (behind a proxy, set {@code server.forward-headers-strategy} so that is known)
 * @param clientAddressHeader a request header that carries the client's address and that the platform in front
 *                         sets itself, so a client cannot choose it (Cloudflare's {@code CF-Connecting-IP}, say).
 *                         Unset, the address is the connection's, or X-Forwarded-For's when a trusted proxy such
 *                         as the bundled nginx sets it. Limits per client address rely on it being true
 */
@ConfigurationProperties("cinescout.security")
public record SecurityProperties(
        @DefaultValue("true") boolean registrationOpen,
        @DefaultValue("14d") Duration sessionTtl,
        Boolean cookieSecure,
        String clientAddressHeader
) {
}
