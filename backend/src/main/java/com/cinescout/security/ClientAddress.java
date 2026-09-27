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
 * <p>
 * As a filter it puts the address into the Reactor context, where code without the exchange (the password check
 * behind HTTP Basic) can read it.
 */
public final class ClientAddress implements WebFilter {

    private static final String KEY = ClientAddress.class.getName();
    private static final String UNKNOWN = "unknown";

    public static String of(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? UNKNOWN : remote.getHostString();
    }

    static String of(ContextView context) {
        return context.getOrDefault(KEY, UNKNOWN);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).contextWrite(context -> context.put(KEY, of(exchange)));
    }
}
