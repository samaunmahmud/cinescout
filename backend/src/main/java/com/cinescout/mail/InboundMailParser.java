package com.cinescout.mail;

import org.springframework.http.HttpHeaders;

/** One inbound-mail provider's webhook: whether a call really comes from it, and the email it carries. */
public interface InboundMailParser {

    /** The name in the webhook's path, e.g. {@code postmark}. */
    String provider();

    /** Whether the call proves it comes from the provider (a signature, or the credentials its URL carries). */
    boolean verify(HttpHeaders headers, byte[] body);

    /** @throws IllegalArgumentException if the body is not what the provider sends */
    InboundMessage parse(byte[] body);
}
