package com.cinescout.llm.watsonx;

import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Exchanges the IBM Cloud API key for a bearer token and caches it until shortly before it
 * expires. Concurrent callers share one in-flight exchange, so a burst of requests after
 * expiry triggers a single IAM call.
 */
public class IamTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(IamTokenProvider.class);

    static final String GRANT_TYPE = "urn:ibm:params:oauth:grant-type:apikey";

    /** Refresh this long before IAM says the token expires, to cover clock skew and in-flight calls. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private final WebClient iam;
    private final String apiKey;
    private volatile Mono<String> cached;

    /** @param iam a client whose base URL is the IAM endpoint */
    public IamTokenProvider(WebClient iam, String apiKey) {
        this.iam = iam;
        this.apiKey = apiKey;
        this.cached = exchange();
    }

    public Mono<String> token() {
        return Mono.defer(() -> cached);
    }

    /** Drops the cached token, e.g. after the API rejected it; the next call fetches a new one. */
    public void invalidate() {
        this.cached = exchange();
    }

    private Mono<String> exchange() {
        return iam.post()
                .uri("/identity/token")
                .body(BodyInserters.fromFormData("grant_type", GRANT_TYPE).with("apikey", apiKey))
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    // A wrong API key comes back as 400, not 401, so any client error except
                    // throttling means our credentials are the problem. The body is not
                    // included: it is IBM's, not ours, and we have no need to echo it.
                    int status = response.statusCode().value();
                    LlmException error = status == 429 || status >= 500
                            ? LlmException.forStatus("IBM IAM", status, null)
                            : new LlmException(Kind.AUTHENTICATION, "IBM IAM rejected the API key (HTTP " + status + ")");
                    return Mono.error(error);
                })
                .bodyToMono(TokenResponse.class)
                .filter(t -> t.accessToken() != null && !t.accessToken().isBlank())
                .switchIfEmpty(Mono.error(() -> new LlmException(Kind.UNAVAILABLE, "IBM IAM returned no access token")))
                .onErrorMap(e -> !(e instanceof LlmException),
                        e -> new LlmException(Kind.UNAVAILABLE, "IBM IAM is unreachable or returned an unreadable response", e))
                .doOnNext(t -> log.debug("Obtained IAM token valid for {}s", t.expiresIn()))
                // ttl <= 0 (token about to expire) means "do not cache"; failures are never cached.
                .cache(TokenResponse::ttl, error -> Duration.ZERO, () -> Duration.ZERO)
                .map(TokenResponse::accessToken);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {

        Duration ttl() {
            Duration ttl = Duration.ofSeconds(expiresIn).minus(EXPIRY_SKEW);
            return ttl.isNegative() ? Duration.ZERO : ttl;
        }
    }
}
