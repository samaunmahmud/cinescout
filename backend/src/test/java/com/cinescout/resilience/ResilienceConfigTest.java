package com.cinescout.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(ResilienceConfig.class);

    @Test
    void hasSensibleDefaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GuardFactory.class);
            ResilienceProperties props = context.getBean(ResilienceProperties.class);
            assertThat(props.maxAttempts()).isEqualTo(3);
            assertThat(props.initialBackoff()).isEqualTo(Duration.ofMillis(500));
            assertThat(props.maxBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(props.breakerWindowSize()).isEqualTo(10);
            assertThat(props.breakerMinimumCalls()).isEqualTo(5);
            assertThat(props.breakerFailureRatePercent()).isEqualTo(50);
            assertThat(props.breakerOpenDuration()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void canBeOverriddenFromConfiguration() {
        runner.withPropertyValues("cinescout.resilience.max-attempts=5", "cinescout.resilience.breaker-open-duration=1m")
                .run(context -> {
                    assertThat(context.getBean(ResilienceProperties.class).maxAttempts()).isEqualTo(5);
                    assertThat(context.getBean(ResilienceProperties.class).breakerOpenDuration()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    void nonsenseSettingsFailStartup() {
        runner.withPropertyValues("cinescout.resilience.max-attempts=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("cinescout.resilience.breaker-failure-rate-percent=101").run(context -> assertThat(context).hasFailed());
    }
}
