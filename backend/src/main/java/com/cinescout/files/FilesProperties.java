package com.cinescout.files;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where uploaded files are kept.
 *
 * @param store      {@code local} (a directory, the default) or {@code s3} (an S3-compatible bucket)
 * @param localDir   the directory for {@code local}
 * @param signingKey the secret that signs short-lived links to files; a random one per start when empty (links then
 *                   stop working at a restart, which they would soon anyway)
 * @param s3         the bucket for {@code s3}
 */
@ConfigurationProperties("cinescout.files")
public record FilesProperties(
        @DefaultValue("local") String store,
        String localDir,
        String signingKey,
        S3 s3
) {

    public record S3(String endpoint, String bucket, @DefaultValue("auto") String region, String accessKey, String secretKey) {

        boolean complete() {
            return notBlank(endpoint) && notBlank(bucket) && notBlank(accessKey) && notBlank(secretKey);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
