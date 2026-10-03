package com.cinescout.files;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FileStoreTest {

    private final WireMockServer s3 = new WireMockServer(options().dynamicPort());
    private S3FileStore store;

    @BeforeEach
    void start() {
        s3.start();
        store = new S3FileStore(WebClient.builder(), new FilesProperties.S3(s3.baseUrl(), "recce", "auto", "AKID", "SECRET"),
                Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        s3.stop();
    }

    @Test
    void putsGetsAndDeletesPathStyleWithSignedRequests() {
        s3.stubFor(put(urlEqualTo("/recce/photos/a/b.jpg")).willReturn(aResponse().withStatus(200)));
        s3.stubFor(get(urlEqualTo("/recce/photos/a/b.jpg")).willReturn(aResponse().withStatus(200).withBody(new byte[] {7, 8})));
        s3.stubFor(delete(urlEqualTo("/recce/photos/a/b.jpg")).willReturn(aResponse().withStatus(204)));

        store.put("photos/a/b.jpg", new byte[] {7, 8}, "image/jpeg").block();
        assertThat(store.get("photos/a/b.jpg").block()).containsExactly(7, 8);
        store.delete("photos/a/b.jpg").block();

        s3.verify(putRequestedFor(urlEqualTo("/recce/photos/a/b.jpg"))
                .withRequestBody(binaryEqualTo(new byte[] {7, 8}))
                .withHeader("Content-Type", equalTo("image/jpeg"))
                .withHeader("x-amz-date", equalTo("20261003T100000Z"))
                .withHeader("x-amz-content-sha256", equalTo(AwsV4Signer.sha256Hex(new byte[] {7, 8})))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 Credential=AKID/20261003/auto/s3/aws4_request, "
                        + "SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=[0-9a-f]{64}")));
        s3.verify(deleteRequestedFor(urlEqualTo("/recce/photos/a/b.jpg")));
    }

    @Test
    void aMissingFileIsEmptyButOtherFailuresAreErrors() {
        s3.stubFor(get(urlEqualTo("/recce/photos/gone.jpg")).willReturn(aResponse().withStatus(404)));
        s3.stubFor(get(urlEqualTo("/recce/photos/denied.jpg")).willReturn(aResponse().withStatus(403)));

        assertThat(store.get("photos/gone.jpg").blockOptional()).isEmpty();
        assertThatThrownBy(() -> store.get("photos/denied.jpg").block()).isInstanceOf(WebClientResponseException.class);
    }
}
