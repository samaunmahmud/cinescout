package com.cinescout.search.parallel;

import com.cinescout.search.LocationSearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Wires the Parallel {@link LocationSearchClient}. It only exists when an API key is
 * configured, so the app still starts for database-only work without any credentials; a
 * service that needs a {@link LocationSearchClient} then fails at startup with a clear
 * missing-bean error instead of at the first request.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.search.parallel.api-key:}'.isBlank()")
@EnableConfigurationProperties(ParallelProperties.class)
class ParallelConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    @Bean
    LocationSearchClient parallelSearchClient(WebClient.Builder builder, ParallelProperties props,
                                              ObjectMapper mapper, Validator validator) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(props.timeout());
        WebClient webClient = builder.clone()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
        return new ParallelSearchClient(webClient, props, mapper, validator);
    }
}
