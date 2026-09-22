package com.cinescout.logistics;

import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.logistics.places.PlacesClient;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.resilience.GuardFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the logistics module. Its providers need no keys, so unlike scouting and outreach it is always on. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LogisticsProperties.class)
class LogisticsConfig {

    @Bean
    LogisticsService logisticsService(WeatherClient weather, PlacesClient places, Geocoder geocoder, GuardFactory guards,
                                      LocationRepository locations, BlockingTransactions db, ObjectMapper mapper,
                                      LogisticsProperties props) {
        return new LogisticsService(weather, places, geocoder, guards, locations, db, mapper, props, Clock.systemUTC());
    }
}
