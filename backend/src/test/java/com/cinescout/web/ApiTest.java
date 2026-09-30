package com.cinescout.web;

import com.cinescout.dto.UserResponse;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/**
 * Base for tests that drive the real application over HTTP (the whole filter chain, security
 * included) against a real PostgreSQL. The container is started once and shared by every context.
 */
@SpringBootTest
// Some calls (scouting, the API description on first use) take seconds; a busy machine can push them past the
// client's default five.
@AutoConfigureWebTestClient(timeout = "PT30S")
abstract class ApiTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected WebTestClient web;

    @Autowired
    protected JdbcTemplate jdbc;

    @AfterEach
    void deleteEverything() {
        jdbc.update("DELETE FROM users"); // the database cascades to everything beneath
    }

    /** A client that sends HTTP Basic credentials with every request. */
    protected WebTestClient as(String email, String password) {
        return web.mutate().defaultHeaders(headers -> headers.setBasicAuth(email, password)).build();
    }

    protected static String uniqueEmail(String name) {
        return name + "-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers an account through the API and returns a client logged in as it. */
    protected Account register(String name) {
        String email = uniqueEmail(name);
        UserResponse user = web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        return new Account(user.id(), email, as(email, PASSWORD));
    }

    record Account(UUID id, String email, WebTestClient client) {
    }
}
