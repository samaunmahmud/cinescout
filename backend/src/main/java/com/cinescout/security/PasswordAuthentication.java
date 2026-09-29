package com.cinescout.security;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Checks an email and password in the same time whether or not the account exists. Spring's
 * {@code UserDetailsRepositoryReactiveAuthenticationManager} answers an unknown email straight away but hashes the
 * password for a known one, so how long a failed login takes tells which emails have accounts; it also turns a
 * disabled account away before looking at the password. Here every attempt costs one password hash: against the
 * account's hash, or against the hash of a random password nobody knows. The account's status is checked only
 * after its password matched, so a wrong guess looks the same whatever state the account is in.
 */
final class PasswordAuthentication implements ReactiveAuthenticationManager {

    private final ReactiveUserDetailsService users;
    private final PasswordEncoder encoder;
    private final AccountStatusUserDetailsChecker status = new AccountStatusUserDetailsChecker();
    /** Made with the same encoder, so it costs what a real account's hash costs. */
    private final String unknownAccountHash;

    PasswordAuthentication(ReactiveUserDetailsService users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.unknownAccountHash = encoder.encode(Base64.getEncoder().encodeToString(secret));
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String presented = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        return users.findByUsername(authentication.getName())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .publishOn(Schedulers.boundedElastic()) // bcrypt is slow on purpose: keep it off the event loop
                .map(account -> {
                    boolean matches = encoder.matches(presented, account.map(UserDetails::getPassword).orElse(unknownAccountHash));
                    if (account.isEmpty() || !matches) {
                        throw new BadCredentialsException("Invalid Credentials");
                    }
                    UserDetails user = account.get();
                    status.check(user);
                    return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                });
    }
}
