package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** With no inbound provider: no reply addresses, no webhook, and replies are recorded by hand without errors. */
class ReplyUnconfiguredApiTest extends ApiTest {

    @Test
    void repliesAreRecordedByHandAndTheWebhookIsNotThere() {
        Account ada = register("Ada");
        String draft = ReplyApiTest.sentDraft(this, ada, jdbc, ReplyApiTest.TOKEN);

        JsonNode loaded = ada.client().get().uri("/api/outreach-drafts/" + draft).exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(loaded.path("replyTo").isNull()).isTrue();
        web.post().uri("/api/inbound/postmark").bodyValue("{}").exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/outreach-drafts/" + draft + "/replies").bodyValue(Map.of("text", "Yes")).exchange()
                .expectStatus().isCreated();
    }
}
