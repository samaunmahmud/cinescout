package com.cinescout.video.youtube;

import com.cinescout.video.VideoSearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Wires the YouTube {@link VideoSearchClient}. It only exists when an API key is configured. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.video.youtube.api-key:}'.isBlank()")
@EnableConfigurationProperties(YouTubeProperties.class)
class YouTubeConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    @Bean
    VideoSearchClient youTubeVideoClient(WebClient.Builder builder, YouTubeProperties props, ObjectMapper mapper) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(props.timeout());
        WebClient webClient = builder.clone()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
        return new YouTubeVideoClient(webClient, props, mapper);
    }
}
