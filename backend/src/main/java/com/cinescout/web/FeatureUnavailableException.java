package com.cinescout.web;

/** The feature exists but this server has not been configured for it (e.g. no AI API keys). Reported as 503. */
public class FeatureUnavailableException extends RuntimeException {

    public FeatureUnavailableException(String message) {
        super(message);
    }
}
