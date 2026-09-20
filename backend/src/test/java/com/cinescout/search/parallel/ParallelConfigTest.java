package com.cinescout.search.parallel;

import com.cinescout.search.LocationSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ValidationAutoConfiguration.class, WebClientAutoConfiguration.class))
            .withUserConfiguration(ParallelConfig.class);

    @Test
    void theAppStartsWithoutSearchWhenNoApiKeyIsConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LocationSearchClient.class));
    }

    @Test
    void aBlankApiKeyCountsAsNotConfigured() {
        // application.yml maps an unset PARALLEL_API_KEY to an empty string.
        runner.withPropertyValues("cinescout.search.parallel.api-key=")
                .run(context -> assertThat(context).doesNotHaveBean(LocationSearchClient.class));
    }

    @Test
    void createsTheClientWithDefaultsWhenAnApiKeyIsSet() {
        runner.withPropertyValues("cinescout.search.parallel.api-key=secret-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(LocationSearchClient.class);
                    assertThat(context.getBean(LocationSearchClient.class)).isInstanceOf(ParallelSearchClient.class);

                    ParallelProperties props = context.getBean(ParallelProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("https://api.parallel.ai");
                    assertThat(props.mode()).isNull();
                    assertThat(props.excerptChars()).isEqualTo(1500);
                    assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void anUnknownModeFailsStartupInsteadOfTheFirstRequest() {
        runner.withPropertyValues("cinescout.search.parallel.api-key=secret-key", "cinescout.search.parallel.mode=warp")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theApiKeyNeverAppearsInToString() {
        ParallelProperties props = new ParallelProperties("secret-key", "https://x", "fast", 1500, Duration.ofSeconds(1));

        assertThat(props.toString()).doesNotContain("secret-key").contains("fast");
    }
}
