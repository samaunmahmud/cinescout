package com.cinescout.permits;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PermitsConfig.PermitsProperties.class)
class PermitsConfig {

    @Bean
    FilmingOffices filmingOffices() {
        return FilmingOffices.load();
    }

    /** @param editUrl where to correct the filming offices file (shown on every office card); empty for none */
    @ConfigurationProperties("cinescout.permits")
    record PermitsProperties(
            @DefaultValue("https://github.com/samaunmahmud/cinescout/edit/main/backend/src/main/resources/permits/filming-offices.yml")
            String editUrl) {
    }
}
