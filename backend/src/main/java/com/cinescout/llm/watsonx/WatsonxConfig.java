package com.cinescout.llm.watsonx;

import com.cinescout.llm.JsonSchemas;
import com.cinescout.llm.LlmClient;
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
 * Wires the watsonx.ai {@link LlmClient}. It only exists when an API key is configured, so
 * the app still starts for database-only work without any IBM credentials; a service that
 * needs an {@link LlmClient} then fails at startup with a clear missing-bean error instead
 * of at the first request.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.llm.watsonx.api-key:}'.isBlank()")
@EnableConfigurationProperties(WatsonxProperties.class)
class WatsonxConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    @Bean
    IamTokenProvider iamTokenProvider(WebClient.Builder builder, WatsonxProperties props) {
        return new IamTokenProvider(webClient(builder, props, props.iamUrl()), props.apiKey());
    }

    @Bean
    JsonSchemas jsonSchemas() {
        return new JsonSchemas();
    }

    @Bean
    LlmClient watsonxLlmClient(WebClient.Builder builder, WatsonxProperties props, IamTokenProvider tokens,
                               JsonSchemas schemas, ObjectMapper mapper, Validator validator) {
        return new WatsonxLlmClient(webClient(builder, props, props.baseUrl()), tokens, props, schemas, mapper, validator);
    }

    private static WebClient webClient(WebClient.Builder builder, WatsonxProperties props, String baseUrl) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(props.timeout());
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}
