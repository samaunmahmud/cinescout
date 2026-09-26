package com.cinescout.video;

import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.resilience.GuardFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires venue videos. Like the client they need, they exist only when the YouTube key is set (a condition on
 * the property, not on the bean, which would depend on registration order); without it the endpoint says 503.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.video.youtube.api-key:}'.isBlank()")
@EnableConfigurationProperties(VideoProperties.class)
class VideoConfig {

    @Bean
    VideoService videoService(VideoSearchClient client, GuardFactory guards, LocationRepository locations, BlockingTransactions db,
                              ObjectMapper mapper, VideoProperties props) {
        return new VideoService(client, guards, locations, db, mapper, props, Clock.systemUTC());
    }
}
