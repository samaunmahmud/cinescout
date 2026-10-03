package com.cinescout.web;

import com.cinescout.mail.InboundMailParser;
import com.cinescout.service.ReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Inbound mail", description = "Where an inbound-mail provider posts the replies to outreach emails.")
class InboundMailController {

    private static final Logger log = LoggerFactory.getLogger(InboundMailController.class);

    private final ObjectProvider<InboundMailParser> parser;
    private final ReplyService replies;

    InboundMailController(ObjectProvider<InboundMailParser> parser, ReplyService replies) {
        this.parser = parser;
        this.replies = replies;
    }

    /**
     * 404 unless this provider is configured; 401 if the call does not prove it comes from the provider; otherwise 200
     * whether or not the email matched a draft, so the provider does not retry it.
     */
    @Operation(summary = "Receive an inbound email", description = "For the configured provider only (postmark).")
    @SecurityRequirements
    @PostMapping("/api/inbound/{provider}")
    Mono<ResponseEntity<Void>> receive(@PathVariable String provider, @RequestHeader HttpHeaders headers, @RequestBody byte[] body) {
        InboundMailParser configured = parser.getIfAvailable();
        if (configured == null || !configured.provider().equalsIgnoreCase(provider)) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        if (!configured.verify(headers, body)) {
            log.warn("An inbound mail webhook call failed verification");
            return Mono.just(ResponseEntity.status(401).build());
        }
        try {
            return replies.receive(configured.parse(body)).thenReturn(ResponseEntity.ok().build());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
    }
}
