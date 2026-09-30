package com.cinescout.llm;

/**
 * Every failure of an {@link LlmClient}, classified so the orchestration layer can decide
 * between retrying, tripping the circuit breaker and giving up without knowing the vendor.
 *
 * <p>Messages are for logs and never contain prompt text or model output, which may be a
 * filmmaker's private script. Do not pass them to API clients verbatim.
 */
public class LlmException extends RuntimeException {

    public enum Kind {
        /** Credentials or project access rejected. Retrying cannot help; configuration must change. */
        AUTHENTICATION(false),
        /**
         * The account's usage allowance (a monthly token quota, say) is used up. Unlike a rate limit, waiting a
         * moment does not help: nothing works until the allowance renews or the plan changes.
         */
        QUOTA_EXHAUSTED(false),
        /** Vendor rate limit hit. Retry with back-off. */
        RATE_LIMITED(true),
        /** Timeout, connection failure or 5xx. Retry with back-off; feeds the circuit breaker. */
        UNAVAILABLE(true),
        /** The vendor rejected our request as malformed. A bug or a config error (e.g. model id). */
        INVALID_REQUEST(false),
        /** The model answered, but not with usable JSON. Sampling is random, so a retry may work. */
        INVALID_OUTPUT(true);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
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
    public static LlmException forStatus(String service, int status, String detail) {
        Kind kind = switch (status) {
            case 401, 403 -> Kind.AUTHENTICATION;
            case 429 -> Kind.RATE_LIMITED;
            case 408 -> Kind.UNAVAILABLE;
            default -> status >= 500 ? Kind.UNAVAILABLE : Kind.INVALID_REQUEST;
        };
        String message = service + " returned HTTP " + status;
        return new LlmException(kind, detail == null || detail.isBlank() ? message : message + ": " + detail);
    }
}
