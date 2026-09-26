package com.cinescout.video;

/**
 * Every failure of a {@link VideoSearchClient}, classified like the other provider exceptions. Messages are
 * for logs and never include the API key.
 */
public class VideoException extends RuntimeException {

    public enum Kind {
        /** The key was rejected or lacks access to the API. Configuration must change. */
        AUTHENTICATION(false),
        /** The daily quota or a rate limit is used up. Retrying later can help. */
        QUOTA_EXCEEDED(false),
        /** Timeout, connection failure, 5xx or an unreadable answer. Retry with back-off; feeds the breaker. */
        UNAVAILABLE(true),
        /** The provider rejected our request as malformed. A bug. */
        INVALID_REQUEST(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public VideoException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public VideoException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind.retryable;
    }
}
