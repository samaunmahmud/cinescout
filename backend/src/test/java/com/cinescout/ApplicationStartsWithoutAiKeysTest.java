package com.cinescout;

import com.cinescout.llm.LlmClient;
import com.cinescout.scouting.SceneScoutingService;
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

/** The documented default: only the database is configured, and the app must still start. */
@SpringBootTest
@Testcontainers
class ApplicationStartsWithoutAiKeysTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    ApplicationContext context;

    @Test
    void theAppStartsAndSimplyHasNoAiBeans() {
        assertThat(context.getBeansOfType(LlmClient.class)).isEmpty();
        assertThat(context.getBeansOfType(LocationSearchClient.class)).isEmpty();
        assertThat(context.getBeansOfType(SceneScoutingService.class)).isEmpty();
    }
}
