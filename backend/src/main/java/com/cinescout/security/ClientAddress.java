package com.cinescout.security;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.net.InetSocketAddress;

/**
 * The address a request came from, for limits kept per client. Behind nginx it is the browser's, taken from
 * X-Forwarded-For ({@code server.forward-headers-strategy}), which nginx sets itself so a client cannot choose it.
 * On a platform with no nginx, it can be read from a header the platform sets ({@code
 * cinescout.security.client-address-header}).
 * <p>
 * As a filter it puts the address into the Reactor context, where code without the exchange (the password check
 * behind HTTP Basic) can read it.
 */
public final class ClientAddress implements WebFilter {

    private static final String KEY = ClientAddress.class.getName();
    private static final String UNKNOWN = "unknown";

    private final String header;

    /** @param header the request header to read the address from, or null for the connection's address */
    public ClientAddress(String header) {
        this.header = header == null || header.isBlank() ? null : header.strip();
    }

    public ClientAddress() {
        this(null);
    }

    /** The address as this filter worked it out for the request; the connection's if the filter did not run. */
    public static String of(ServerWebExchange exchange) {
        String known = exchange.getAttribute(KEY);
        return known != null ? known : remote(exchange);
    }

    static String of(ContextView context) {
        return context.getOrDefault(KEY, UNKNOWN);
    }

    private static String remote(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? UNKNOWN : remote.getHostString();
    }

    String resolve(ServerWebExchange exchange) {
        if (header != null) {
            String value = exchange.getRequest().getHeaders().getFirst(header);
            if (value != null && !value.isBlank()) {
                // A header may hold a list; the platform's own entry is the first.
                return value.split(",")[0].strip();
            }
        }
        return remote(exchange);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String address = resolve(exchange);
        exchange.getAttributes().put(KEY, address);
        return chain.filter(exchange).contextWrite(context -> context.put(KEY, address));
    }
}
