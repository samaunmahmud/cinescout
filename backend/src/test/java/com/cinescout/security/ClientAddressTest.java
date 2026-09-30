package com.cinescout.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    private static MockServerWebExchange exchange(String header, String value) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/projects")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 443));
        if (header != null) {
            request.header(header, value);
        }
        return MockServerWebExchange.from(request);
    }

    private static String seenBy(ClientAddress filter, MockServerWebExchange exchange) {
        AtomicReference<String> fromContext = new AtomicReference<>();
        filter.filter(exchange, ex -> Mono.deferContextual(context -> {
            fromContext.set(ClientAddress.of(context));
            return Mono.empty();
        })).block();
        assertThat(ClientAddress.of(exchange)).isEqualTo(fromContext.get());
        return fromContext.get();
    }

    @Test
    void withoutAHeaderItIsTheConnectionsAddress() {
        assertThat(seenBy(new ClientAddress(), exchange("CF-Connecting-IP", "203.0.113.5"))).isEqualTo("10.0.0.7");
    }

    @Test
    void withAHeaderItIsThePlatformsEntryInIt() {
        ClientAddress filter = new ClientAddress("CF-Connecting-IP");

        assertThat(seenBy(filter, exchange("CF-Connecting-IP", "203.0.113.5"))).isEqualTo("203.0.113.5");
        assertThat(seenBy(filter, exchange("CF-Connecting-IP", " 203.0.113.5 , 198.51.100.1"))).isEqualTo("203.0.113.5");
        // Missing on a request (a health check from inside, say): the connection's address.
        assertThat(seenBy(filter, exchange(null, null))).isEqualTo("10.0.0.7");
    }
}
