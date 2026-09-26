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
 */
@ConfigurationProperties("cinescout.security")
public record SecurityProperties(
        @DefaultValue("true") boolean registrationOpen,
        @DefaultValue("14d") Duration sessionTtl,
        Boolean cookieSecure
) {
}
