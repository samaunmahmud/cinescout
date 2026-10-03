package com.cinescout.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailParsingTest {

    @Test
    void aReplyAddressCarriesTheDraftsTokenWhateverItsCase() {
        ReplyAddresses addresses = new ReplyAddresses("Replies.Example.com", "scout");

        assertThat(addresses.address("abc123def4567890")).isEqualTo("scout+abc123def4567890@replies.example.com");
        assertThat(addresses.tokenOf("SCOUT+ABC123DEF4567890@replies.example.COM")).contains("abc123def4567890");
        assertThat(addresses.tokenOf("scout+abc123def4567890@other.example.com")).isEmpty();
        assertThat(addresses.tokenOf("someone@replies.example.com")).isEmpty();
        assertThat(addresses.tokenOf("scout+short@replies.example.com")).isEmpty();
        assertThat(new ReplyAddresses("", "scout").address("abc")).isNull();
    }

    @Test
    void postmarkIsBelievedOnlyWithTheCredentialsInItsWebhookUrl() {
        PostmarkInboundParser parser = new PostmarkInboundParser(new ObjectMapper(), "hook", "s3cret");
        HttpHeaders right = new HttpHeaders();
        right.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString("hook:s3cret".getBytes(StandardCharsets.UTF_8)));
        HttpHeaders wrong = new HttpHeaders();
        wrong.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString("hook:guess".getBytes(StandardCharsets.UTF_8)));

        assertThat(parser.verify(right, new byte[0])).isTrue();
        assertThat(parser.verify(wrong, new byte[0])).isFalse();
        assertThat(parser.verify(new HttpHeaders(), new byte[0])).isFalse();
    }

    @Test
    void aPostmarkMessageGivesItsRecipientsSenderAndTheReplyWithoutTheQuotedThread() {
        String json = """
                {"From":"owner@diner.example","FromName":"Sal","FromFull":{"Email":"owner@diner.example","Name":"Sal Russo"},
                 "ToFull":[{"Email":"ada@prod.example"}],"CcFull":[{"Email":"scout+abc123def4567890@replies.example.com"}],
                 "OriginalRecipient":"scout+abc123def4567890@replies.example.com","Subject":"Re: Filming at the diner",
                 "Date":"Fri, 2 Oct 2026 14:05:00 +0100","TextBody":"Yes, Tuesdays work.\\n\\n> On Thu, Ada wrote:","StrippedTextReply":"Yes, Tuesdays work."}
                """;
        InboundMessage message = new PostmarkInboundParser(new ObjectMapper(), "u", "p").parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(message.recipients()).contains("ada@prod.example", "scout+abc123def4567890@replies.example.com");
        assertThat(message.fromAddress()).isEqualTo("owner@diner.example");
        assertThat(message.fromName()).isEqualTo("Sal Russo");
        assertThat(message.subject()).isEqualTo("Re: Filming at the diner");
        assertThat(message.text()).isEqualTo("Yes, Tuesdays work.");
        assertThat(message.receivedAt()).isEqualTo(Instant.parse("2026-10-02T13:05:00Z"));
        assertThatThrownBy(() -> new PostmarkInboundParser(new ObjectMapper(), "u", "p").parse("[1]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
