package com.cinescout.security;

import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimitExceededException;
import com.cinescout.ratelimit.RateLimiter;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * Limits failed password checks per client address, for the login form and HTTP Basic alike. Each check takes one
 * attempt up front, so a burst of parallel guesses cannot all get through, and a successful one gives it back:
 * only failures count.
 */
final class ThrottledAuthentication implements ReactiveAuthenticationManager {

    private final ReactiveAuthenticationManager passwords;
    private final RateLimiter limits;

    ThrottledAuthentication(ReactiveAuthenticationManager passwords, RateLimiter limits) {
        this.passwords = passwords;
        this.limits = limits;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.deferContextual(context -> {
            String client = ClientAddress.of(context);
            return limits.acquire(RateLimit.LOGIN, client)
                    .onErrorMap(RateLimitExceededException.class, LoginThrottledException::new)
                    .then(Mono.defer(() -> passwords.authenticate(authentication)))
                    .doOnNext(ok -> limits.refund(RateLimit.LOGIN, client));
        });
    }
}
