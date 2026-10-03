package com.cinescout.mail;

import java.time.Instant;
import java.util.List;

/** An email that came in, as a provider's webhook describes it: only what reply tracking keeps. */
public record InboundMessage(List<String> recipients, String fromAddress, String fromName, String subject, Instant receivedAt,
                             String text) {
}
