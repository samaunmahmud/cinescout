package com.cinescout.outreach;

import com.cinescout.llm.LlmClient;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.ProjectAccess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Loads the real configuration classes, so this also proves outreach wires to the real watsonx.ai client. */
class OutreachConfigTest {

    private static final String[] LLM = {
            "cinescout.llm.watsonx.api-key=k", "cinescout.llm.watsonx.project-id=p", "cinescout.llm.watsonx.model-id=ibm/m"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ValidationAutoConfiguration.class, WebClientAutoConfiguration.class))
            .withUserConfiguration(OutreachConfig.class,
                    configClass("com.cinescout.resilience.ResilienceConfig"),
                    configClass("com.cinescout.llm.watsonx.WatsonxConfig"))
            // The persistence beans come from JPA, which this slice does not load.
            .withBean(LocationRepository.class, () -> mock(LocationRepository.class))
            .withBean(OutreachDraftRepository.class, () -> mock(OutreachDraftRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(ProjectAccess.class, () -> mock(ProjectAccess.class))
            .withBean(BlockingTransactions.class, () -> mock(BlockingTransactions.class));

    /** The wiring classes are deliberately package-private, so tests in other packages load them by name. */
    private static Class<?> configClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void withoutTheLlmKeyThereIsNoGenerator() {
        runner.run(context -> assertThat(context).doesNotHaveBean(OutreachGenerationService.class));
    }

    @Test
    void theLlmKeyAloneIsEnoughBecauseOutreachNeverSearches() {
        runner.withPropertyValues(LLM).run(context -> {
            assertThat(context).hasSingleBean(OutreachGenerationService.class);
            assertThat(context.getBean(LlmClient.class).modelId()).isEqualTo("ibm/m");
        });
    }
}
