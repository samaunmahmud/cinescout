package com.cinescout;

import com.cinescout.llm.LlmClient;
import com.cinescout.scouting.SceneScoutingService;
import com.cinescout.scouting.ScoutingPipeline;
import com.cinescout.search.LocationSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application (Flyway, JPA, WebFlux, resilience, both clients) against a real
 * PostgreSQL with dummy API keys, so wiring mistakes surface here rather than at deploy time.
 * Nothing calls IBM or Parallel: the keys are never used.
 */
@SpringBootTest(properties = {
        "cinescout.llm.watsonx.api-key=dummy",
        "cinescout.llm.watsonx.project-id=dummy",
        "cinescout.llm.watsonx.model-id=dummy",
        "cinescout.search.parallel.api-key=dummy"
})
@Testcontainers
class ApplicationStartsWithScoutingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    ApplicationContext context;

    @Test
    void everyScoutingBeanIsWiredFromTheRealComponents() {
        assertThat(context.getBean(LlmClient.class)).isNotNull();
        assertThat(context.getBean(LocationSearchClient.class)).isNotNull();
        assertThat(context.getBean(ScoutingPipeline.class)).isNotNull();
        assertThat(context.getBean(SceneScoutingService.class)).isNotNull();
    }
}
