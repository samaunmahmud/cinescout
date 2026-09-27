package com.cinescout.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The 401 for a missing or wrong login, as an RFC 9457 problem like every other error. The message is
 * the same whether the account exists or not, so it cannot be used to discover accounts.
 * <p>
 * A browser answers {@code WWW-Authenticate: Basic} with its own login dialog, even for a script's request.
 * The web app marks its requests with {@code X-Requested-With: XMLHttpRequest} and shows its own login form,
 * so those requests get the 401 without the challenge header.
 * <p>
 * A client that has failed to log in too often gets a 429 with a Retry-After instead, whatever it sent.
 */
final class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper mapper;

    ProblemAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (ex instanceof LoginThrottledException throttled) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(throttled.limit().retryAfterSeconds()));
            return write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests", throttled.limit().detail());
        }
        if (!isScriptRequest(exchange)) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"CineScout\", charset=\"UTF-8\"");
        }
        return write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Valid credentials are required");
    }

    private Mono<Void> write(ServerHttpResponse response, HttpStatus status, String title, String detail) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "type", "about:blank",
                    "title", title,
                    "status", status.value(),
                    "detail", detail));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private static boolean isScriptRequest(ServerWebExchange exchange) {
        return "XMLHttpRequest".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst("X-Requested-With"));
    }
}
