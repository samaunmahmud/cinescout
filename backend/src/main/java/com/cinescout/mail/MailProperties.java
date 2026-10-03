package com.cinescout.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reply tracking for outreach email.
 *
 * @param replyDomain    the domain replies come back to (its mail handled by the inbound provider); empty turns reply
 *                       addresses off, and replies are recorded by hand
 * @param replyLocalPart what comes before the "+" in a reply address
 * @param inbound        the inbound-mail provider that posts replies to the webhook
 */
@ConfigurationProperties("cinescout.mail")
public record MailProperties(String replyDomain, @DefaultValue("scout") String replyLocalPart, Inbound inbound) {

    /**
     * @param provider {@code postmark}, or empty for none (the webhook then answers 404)
     * @param username with {@code password}: the HTTP Basic credentials the provider's webhook URL carries
     */
    public record Inbound(String provider, String username, String password) {
    }

    public boolean replyAddresses() {
        return replyDomain != null && !replyDomain.isBlank();
    }
}
