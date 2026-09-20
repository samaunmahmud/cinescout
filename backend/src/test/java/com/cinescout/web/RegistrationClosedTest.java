package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cinescout.security.registration-open=false")
class RegistrationClosedTest extends ApiTest {

    @Test
    void whenRegistrationIsClosedNoAccountIsCreated() {
        web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", uniqueEmail("late"), "password", PASSWORD, "displayName", "Late"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Registration is closed");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }
}
