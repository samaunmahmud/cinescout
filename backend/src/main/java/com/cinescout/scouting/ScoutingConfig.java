package com.cinescout.scouting;

import com.cinescout.llm.LlmClient;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.search.LocationSearchClient;
import com.cinescout.service.ProjectAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires scouting. It needs both external clients, which exist only when their API keys are set, so
 * it is conditional on the same two properties rather than on the beans themselves (bean-presence
 * conditions depend on registration order). Without both keys the app still starts for
 * database-only work, and nothing scouting-related exists to inject.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.llm.watsonx.api-key:}'.isBlank() && !'${cinescout.search.parallel.api-key:}'.isBlank()")
@EnableConfigurationProperties(ScoutingProperties.class)
class ScoutingConfig {

    @Bean
    ScoutingPipeline scoutingPipeline(LlmClient llm, LocationSearchClient search, GuardFactory guards,
                                      ScoutingProperties props) {
        return new ScoutingPipeline(llm, search, guards, props);
    }

    @Bean
    VenuePlacer venuePlacer(Geocoder geocoder, ScoutingProperties props) {
        return new VenuePlacer(geocoder, props.placementBudget());
    }

    @Bean
    SceneScoutingService sceneScoutingService(ScoutingPipeline pipeline, VenuePlacer placer, SceneRepository scenes,
                                              ProjectRepository projects, LocationRepository locations,
                                              ProjectAccess access, BlockingTransactions db, ObjectMapper mapper) {
        return new SceneScoutingService(pipeline, placer, scenes, projects, locations, access, db, mapper);
    }
}
