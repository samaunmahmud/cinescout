package com.cinescout.logistics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Shared logistics settings. The providers need no keys, so the module is always on. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LogisticsProperties.class)
class LogisticsConfig {
}
