package com.cinescout.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param registrationOpen whether anyone may create an account. Turn off ({@code cinescout.security.registration-open=false})
 *                         once the accounts you want exist, for a deployment that is not meant to be public
 */
@ConfigurationProperties("cinescout.security")
public record SecurityProperties(@DefaultValue("true") boolean registrationOpen) {
}
