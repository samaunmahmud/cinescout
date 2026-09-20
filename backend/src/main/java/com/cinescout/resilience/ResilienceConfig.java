package com.cinescout.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResilienceProperties.class)
class ResilienceConfig {

    @Bean
    GuardFactory guardFactory(ResilienceProperties props) {
        return new GuardFactory(props, CircuitBreakerRegistry.ofDefaults());
    }
}
