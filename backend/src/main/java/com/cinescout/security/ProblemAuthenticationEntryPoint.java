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
 */
final class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper mapper;

    ProblemAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"CineScout\", charset=\"UTF-8\"");
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "type", "about:blank",
                    "title", "Unauthorized",
                    "status", 401,
                    "detail", "Valid credentials are required"));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
