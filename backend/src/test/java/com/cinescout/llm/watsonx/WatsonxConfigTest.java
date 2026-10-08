package com.cinescout.llm.watsonx;

import com.cinescout.llm.LlmClient;
import com.cinescout.llm.RateLimitedLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WatsonxConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ValidationAutoConfiguration.class, WebClientAutoConfiguration.class))
            .withUserConfiguration(WatsonxConfig.class);

    @Test
    void theAppStartsWithoutAnLlmWhenNoApiKeyIsConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void aBlankApiKeyCountsAsNotConfigured() {
        // application.yml maps an unset WATSONX_API_KEY to an empty string.
        runner.withPropertyValues("cinescout.llm.watsonx.api-key=")
                .run(context -> assertThat(context).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void createsTheClientWithDefaultsWhenFullyConfigured() {
        runner.withPropertyValues(
                        "cinescout.llm.watsonx.api-key=secret-key",
                        "cinescout.llm.watsonx.project-id=proj-1",
                        "cinescout.llm.watsonx.model-id=ibm/test-model")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(RateLimitedLlmClient.class); // around the watsonx client

                    WatsonxProperties props = context.getBean(WatsonxProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("https://us-south.ml.cloud.ibm.com");
                    assertThat(props.iamUrl()).isEqualTo("https://iam.cloud.ibm.com");
                    assertThat(props.apiVersion()).isEqualTo("2024-03-14");
                    assertThat(props.temperature()).isZero();
                    assertThat(props.strictSchema()).isTrue();
                    assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void anApiKeyWithoutAProjectOrModelFailsStartupInsteadOfTheFirstRequest() {
        runner.withPropertyValues("cinescout.llm.watsonx.api-key=secret-key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theApiKeyNeverAppearsInToString() {
        WatsonxProperties props = new WatsonxProperties("secret-key", "proj-1", "ibm/test-model",
                "https://x", "https://y", "2024-03-14", 0, 1024, true, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), "ibm/test-vision-model");

        assertThat(props.toString()).doesNotContain("secret-key").contains("proj-1");
    }
}
