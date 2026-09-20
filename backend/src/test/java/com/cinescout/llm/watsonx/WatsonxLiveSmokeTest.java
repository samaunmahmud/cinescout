package com.cinescout.llm.watsonx;

import com.cinescout.domain.SceneRequirements;
import com.cinescout.llm.JsonSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One real call to watsonx.ai, to check the wire format the WireMock tests can only assume
 * (request shape, {@code response_format}, strict schema). Skipped unless explicitly enabled:
 *
 * <pre>
 * WATSONX_LIVE_TEST=true WATSONX_API_KEY=... WATSONX_PROJECT_ID=... WATSONX_MODEL_ID=... \
 *   mvn test -Dtest=WatsonxLiveSmokeTest
 * </pre>
 *
 * Optional: {@code WATSONX_URL} for a non-Dallas region. It bills against the given project.
 */
@EnabledIfEnvironmentVariable(named = "WATSONX_LIVE_TEST", matches = "true")
class WatsonxLiveSmokeTest {

    @Test
    void extractsRequirementsFromARealScene() {
        String baseUrl = System.getenv().getOrDefault("WATSONX_URL", "https://us-south.ml.cloud.ibm.com");
        WatsonxProperties props = new WatsonxProperties(
                System.getenv("WATSONX_API_KEY"), System.getenv("WATSONX_PROJECT_ID"), System.getenv("WATSONX_MODEL_ID"),
                baseUrl, "https://iam.cloud.ibm.com", "2024-03-14", 0, 1024, true, Duration.ofSeconds(60));
        ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

        try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory()) {
            IamTokenProvider tokens = new IamTokenProvider(WebClient.create(props.iamUrl()), props.apiKey());
            WatsonxLlmClient client = new WatsonxLlmClient(WebClient.create(baseUrl), tokens, props,
                    new JsonSchemas(), json, validators.getValidator());

            SceneRequirements requirements = client.generate(
                    "Extract the physical filming requirements of the scene as JSON. Use null when the scene does not say.",
                    "INT. ROOFTOP BAR - NIGHT. Neon signs hum over a crowded terrace; two dozen extras, a live band, "
                            + "and a tense handoff between two strangers.",
                    SceneRequirements.class).block();

            System.out.println("watsonx.ai live result: " + requirements);
            assertThat(requirements).isNotNull();
            assertThat(requirements.settingType()).isNotBlank();
        }
    }
}
