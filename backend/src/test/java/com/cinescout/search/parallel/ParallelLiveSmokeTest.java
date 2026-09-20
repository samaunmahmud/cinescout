package com.cinescout.search.parallel;

import com.cinescout.ai.SearchResult;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.search.LocationSearchRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One real Parallel search, to check the wire format the WireMock tests can only assume.
 * Skipped unless explicitly enabled (it uses your Parallel quota):
 *
 * <pre>
 * PARALLEL_LIVE_TEST=true PARALLEL_API_KEY=... mvn test -Dtest=ParallelLiveSmokeTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "PARALLEL_LIVE_TEST", matches = "true")
class ParallelLiveSmokeTest {

    @Test
    void findsRealVenuesForARooftopScene() {
        String baseUrl = System.getenv().getOrDefault("PARALLEL_URL", "https://api.parallel.ai");
        ParallelProperties props = new ParallelProperties(System.getenv("PARALLEL_API_KEY"), baseUrl, null, 1500,
                Duration.ofSeconds(60));

        try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory()) {
            ParallelSearchClient client = new ParallelSearchClient(WebClient.create(baseUrl), props,
                    Jackson2ObjectMapperBuilder.json().build(), validators.getValidator());

            List<SearchResult> results = client.search(LocationSearchRequest.of(
                    new SceneRequirements("rooftop bar", "neon noir", null, "night", AcousticSensitivity.MEDIUM, 12),
                    "Brooklyn, New York")).block();

            results.forEach(r -> System.out.println("parallel live result: " + r.title() + " " + r.url()));
            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(r -> assertThat(r.provider()).isEqualTo("parallel"));
        }
    }
}
