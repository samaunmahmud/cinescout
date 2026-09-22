package com.cinescout.logistics;

/**
 * Every failure of a logistics provider (weather, places, geocoding), classified so the orchestration
 * layer can decide between retrying, tripping the circuit breaker and giving up without knowing the
 * vendor. The providers are keyless, so there is no authentication kind: a refusal is a bad request
 * (or a blocked User-Agent), which retrying cannot fix.
 *
 * <p>Messages are for logs. They never contain coordinates or addresses, which locate a production's
 * venues; do not pass them to API clients verbatim.
 */
public class LogisticsException extends RuntimeException {

    public enum Kind {
        /** Provider rate limit hit, or its fair-use queue is full. Retry with back-off. */
        RATE_LIMITED(true),
        /** Timeout, connection failure, 5xx or an unreadable answer. Retry with back-off; feeds the breaker. */
        UNAVAILABLE(true),
        /** The provider rejected our request. A bug or a config error. */
        INVALID_REQUEST(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public LogisticsException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LogisticsException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind.retryable;
    }

    /** Maps an HTTP error status from a provider to a {@link Kind}. */
    public static LogisticsException forStatus(String service, int status) {
        Kind kind = switch (status) {
            case 429 -> Kind.RATE_LIMITED;
            case 408 -> Kind.UNAVAILABLE;
            default -> status >= 500 ? Kind.UNAVAILABLE : Kind.INVALID_REQUEST;
        };
        return new LogisticsException(kind, service + " returned HTTP " + status);
    }
}
