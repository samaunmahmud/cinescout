package com.cinescout.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(FilesProperties.class)
class FilesConfig {

    private static final Logger log = LoggerFactory.getLogger(FilesConfig.class);

    /**
     * The S3-compatible store when {@code store=s3} and the bucket is fully configured; otherwise the local disk, so a
     * missing key degrades to files that last until the next deploy instead of a server that will not start.
     */
    @Bean
    FileStore fileStore(FilesProperties props, WebClient.Builder http) {
        if ("s3".equalsIgnoreCase(props.store())) {
            if (props.s3() != null && props.s3().complete()) {
                log.info("Files are kept in the S3-compatible bucket {} at {}", props.s3().bucket(), props.s3().endpoint());
                return new S3FileStore(http, props.s3(), Clock.systemUTC());
            }
            log.warn("FILES_STORE=s3 but the bucket is not fully configured (endpoint, bucket, access and secret key); using the local disk");
        }
        String dir = props.localDir() == null || props.localDir().isBlank()
                ? System.getProperty("java.io.tmpdir") + "/cinescout-files" : props.localDir();
        log.info("Files are kept on the local disk in {}", dir);
        return new LocalDiskFileStore(Path.of(dir));
    }

    @Bean
    FileLinks fileLinks(FilesProperties props) {
        FileLinks links = new FileLinks(props.signingKey(), Clock.systemUTC());
        FileLinks.install(links);
        return links;
    }
}
