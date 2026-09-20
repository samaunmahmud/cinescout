package com.cinescout.scouting;

import com.cinescout.llm.watsonx.WatsonxLlmClient;
import com.cinescout.search.parallel.ParallelSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the real configuration classes, so this also proves the four clients wire together. */
class ScoutingConfigTest {

    private static final String[] LLM = {
            "cinescout.llm.watsonx.api-key=k", "cinescout.llm.watsonx.project-id=p", "cinescout.llm.watsonx.model-id=m"};
    private static final String[] SEARCH = {"cinescout.search.parallel.api-key=k"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ValidationAutoConfiguration.class, WebClientAutoConfiguration.class))
            .withUserConfiguration(ScoutingConfig.class,
                    configClass("com.cinescout.resilience.ResilienceConfig"),
                    configClass("com.cinescout.llm.watsonx.WatsonxConfig"),
                    configClass("com.cinescout.search.parallel.ParallelConfig"));

    /** The wiring classes are deliberately package-private, so tests in other packages load them by name. */
    private static Class<?> configClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theAppStartsWithoutScoutingWhenNeitherKeyIsSet() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ScoutingPipeline.class));
    }

    @Test
    void oneKeyAloneIsNotEnough() {
        runner.withPropertyValues(LLM).run(context -> assertThat(context).doesNotHaveBean(ScoutingPipeline.class));
        runner.withPropertyValues(SEARCH).run(context -> assertThat(context).doesNotHaveBean(ScoutingPipeline.class));
    }

    @Test
    void withBothKeysThePipelineIsBuiltFromTheRealClients() {
        runner.withPropertyValues(LLM).withPropertyValues(SEARCH).run(context -> {
            assertThat(context).hasSingleBean(ScoutingPipeline.class);
            assertThat(context.getBean(com.cinescout.llm.LlmClient.class)).isInstanceOf(WatsonxLlmClient.class);
            assertThat(context.getBean(com.cinescout.search.LocationSearchClient.class)).isInstanceOf(ParallelSearchClient.class);
            assertThat(context.getBean(ScoutingProperties.class).assessmentConcurrency()).isEqualTo(4);
        });
    }

    @Test
    void anOutOfRangeConcurrencyFailsStartup() {
        runner.withPropertyValues(LLM).withPropertyValues(SEARCH)
                .withPropertyValues("cinescout.scouting.assessment-concurrency=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
