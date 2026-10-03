package com.cinescout.files;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Files in an S3-compatible bucket (Cloudflare R2, MinIO, AWS S3), addressed path-style
 * ({@code <endpoint>/<bucket>/<key>}) and signed with Signature Version 4. Keys are checked to plain characters, so
 * they need no further encoding.
 */
public class S3FileStore implements FileStore {

    private static final String EMPTY_SHA256 = AwsV4Signer.sha256Hex(new byte[0]);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient http;
    private final URI endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final Clock clock;

    public S3FileStore(WebClient.Builder http, FilesProperties.S3 props, Clock clock) {
        this.http = http.build();
        this.endpoint = URI.create(props.endpoint().replaceAll("/+$", ""));
        this.bucket = props.bucket();
        this.region = props.region();
        this.accessKey = props.accessKey();
        this.secretKey = props.secretKey();
        this.clock = clock;
    }

    @Override
    public Mono<Void> put(String key, byte[] content, String contentType) {
        return Mono.defer(() -> {
            Map<String, String> headers = signed(HttpMethod.PUT, key, AwsV4Signer.sha256Hex(content), contentType);
            return http.put().uri(url(key)).headers(h -> headers.forEach(h::set)).bodyValue(content)
                    .retrieve().toBodilessEntity().timeout(TIMEOUT).then();
        });
    }

    @Override
    public Mono<byte[]> get(String key) {
        return Mono.defer(() -> {
            Map<String, String> headers = signed(HttpMethod.GET, key, EMPTY_SHA256, null);
            return http.get().uri(url(key)).headers(h -> headers.forEach(h::set))
                    .retrieve().bodyToMono(byte[].class).timeout(TIMEOUT)
                    .onErrorResume(WebClientResponseException.class,
                            error -> error.getStatusCode() == HttpStatus.NOT_FOUND ? Mono.empty() : Mono.error(error));
        });
    }

    @Override
    public Mono<Void> delete(String key) {
        return Mono.defer(() -> {
            Map<String, String> headers = signed(HttpMethod.DELETE, key, EMPTY_SHA256, null);
            return http.delete().uri(url(key)).headers(h -> headers.forEach(h::set))
                    .retrieve().toBodilessEntity().timeout(TIMEOUT)
                    .onErrorResume(WebClientResponseException.class,
                            error -> error.getStatusCode() == HttpStatus.NOT_FOUND ? Mono.empty() : Mono.error(error))
                    .then();
        });
    }

    private String path(String key) {
        return endpoint.getRawPath() + "/" + bucket + "/" + FileKeys.checked(key);
    }

    private URI url(String key) {
        return URI.create(endpoint.getScheme() + "://" + endpoint.getRawAuthority() + path(key));
    }

    private Map<String, String> signed(HttpMethod method, String key, String payloadHash, String contentType) {
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", endpoint.getRawAuthority());
        headers.put("x-amz-content-sha256", payloadHash);
        if (contentType != null) {
            headers.put("content-type", contentType);
        }
        Map<String, String> all = AwsV4Signer.sign(method.name(), path(key), headers, payloadHash, accessKey, secretKey, region, "s3",
                clock.instant());
        // The client sets Host itself, from the URL.
        all.remove("host");
        return all;
    }
}
