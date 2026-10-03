package com.cinescout.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Postmark's inbound webhook. Postmark does not sign its webhooks; it proves itself with the HTTP Basic credentials
 * written into the webhook URL ({@code https://user:pass@host/api/inbound/postmark}), checked here in constant time.
 * The reply's own text ({@code StrippedTextReply}) is preferred over the whole body with the quoted thread.
 */
public class PostmarkInboundParser implements InboundMailParser {

    private final ObjectMapper mapper;
    private final byte[] expected;

    public PostmarkInboundParser(ObjectMapper mapper, String username, String password) {
        this.mapper = mapper;
        this.expected = ("Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String provider() {
        return "postmark";
    }

    @Override
    public boolean verify(HttpHeaders headers, byte[] body) {
        String given = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return given != null && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InboundMessage parse(byte[] body) {
        JsonNode mail;
        try {
            mail = mapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("Not JSON", e);
        }
        if (mail == null || !mail.isObject()) {
            throw new IllegalArgumentException("Not a Postmark inbound message");
        }
        List<String> recipients = new ArrayList<>();
        for (String field : new String[] {"ToFull", "CcFull", "BccFull"}) {
            mail.path(field).forEach(to -> recipients.add(to.path("Email").asText()));
        }
        recipients.add(mail.path("OriginalRecipient").asText(""));
        String reply = mail.path("StrippedTextReply").asText("");
        String text = reply.isBlank() ? mail.path("TextBody").asText("") : reply;
        JsonNode from = mail.path("FromFull");
        return new InboundMessage(recipients.stream().filter(r -> !r.isBlank()).toList(),
                from.path("Email").asText(mail.path("From").asText(null)),
                blankToNull(from.path("Name").asText(mail.path("FromName").asText(null))),
                blankToNull(mail.path("Subject").asText(null)), date(mail.path("Date").asText(null)), text);
    }

    /** Postmark passes the email's own Date header (RFC 1123); a missing or odd one means now. */
    private static Instant date(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(value.strip().replaceAll("\\s+\\(.*\\)$", ""), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
