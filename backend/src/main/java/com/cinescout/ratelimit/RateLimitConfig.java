package com.cinescout.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

    @Bean
    RateLimiter rateLimiter(RateLimitProperties props) {
        return new RateLimiter(props, Clock.systemUTC());
    }
}
