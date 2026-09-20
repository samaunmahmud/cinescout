package com.cinescout.search;

/**
 * Every failure of a {@link LocationSearchClient}, classified so the orchestration layer can
 * decide between retrying, tripping the circuit breaker and giving up without knowing the
 * vendor. Mirrors {@code LlmException}; the two are kept separate because a search outage and
 * an LLM outage are handled (and degrade the product) differently.
 *
 * <p>Messages are for logs and never contain the query, which is derived from a filmmaker's
 * script. Do not pass them to API clients verbatim.
 */
public class SearchException extends RuntimeException {

    public enum Kind {
        /** Credentials rejected. Retrying cannot help; configuration must change. */
        AUTHENTICATION(false),
        /** Vendor rate limit hit. Retry with back-off. */
        RATE_LIMITED(true),
        /** Timeout, connection failure or 5xx. Retry with back-off; feeds the circuit breaker. */
        UNAVAILABLE(true),
        /** The vendor rejected our request as malformed. A bug or a config error. */
        INVALID_REQUEST(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public SearchException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public SearchException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind.retryable;
    }

    /** Maps an HTTP error status from an upstream service to a {@link Kind}. */
    public static SearchException forStatus(String service, int status, String detail) {
        Kind kind = switch (status) {
            case 401, 403 -> Kind.AUTHENTICATION;
            case 429 -> Kind.RATE_LIMITED;
            case 408 -> Kind.UNAVAILABLE;
            default -> status >= 500 ? Kind.UNAVAILABLE : Kind.INVALID_REQUEST;
        };
        String message = service + " returned HTTP " + status;
        return new SearchException(kind, detail == null || detail.isBlank() ? message : message + ": " + detail);
    }
}
