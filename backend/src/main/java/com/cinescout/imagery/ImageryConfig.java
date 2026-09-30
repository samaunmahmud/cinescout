package com.cinescout.imagery;

import com.cinescout.logistics.LogisticsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
class ImageryConfig {

    /** Keyless and always on, like the logistics providers, and it introduces itself the same way. */
    @Bean
    PageImageFinder pageImageFinder(WebClient.Builder builder, LogisticsProperties logistics) {
        return new PageImageFinder(builder, logistics.userAgent(), PublicAddresses::isPublic);
    }
}
