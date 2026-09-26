package com.cinescout.security;

import com.cinescout.domain.AuthSession;
import com.cinescout.domain.User;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.AuthSessionRepository;
import com.cinescout.repository.UserRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Web app logins. A session is a random 256-bit token: the browser keeps it, the database keeps only its
 * SHA-256 hash (a fast hash is enough for a random secret, unlike a password). Sessions expire after
 * {@code ttl} and end at logout; a disabled account's sessions stop working at once.
 */
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final BlockingTransactions db;
    private final Duration ttl;
    private final Clock clock;

    public SessionService(AuthSessionRepository sessions, UserRepository users, BlockingTransactions db, Duration ttl, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.db = db;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Starts a session for the account and returns its token; expired sessions are cleared out on the way. */
    public Mono<String> open(UUID userId) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return db.call(() -> {
            Instant now = clock.instant();
            sessions.deleteExpired(now);
            User user = users.getReferenceById(userId);
            sessions.save(new AuthSession(user, hash(token), now.plus(ttl)));
            return token;
        });
    }

    /** The account a live session belongs to, or empty for an unknown, expired or disabled one. */
    public Mono<AuthenticatedUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return db.call(() -> sessions.findWithUserByTokenHash(hash(token))
                .filter(session -> session.getExpiresAt().isAfter(clock.instant()))
                .map(AuthSession::getUser)
                .filter(User::isEnabled)
                .map(AuthenticatedUser::from)
                .orElse(null));
    }

    /** Ends the session; an unknown token is ignored, so logging out twice is harmless. */
    public Mono<Void> close(String token) {
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return db.run(() -> sessions.deleteByTokenHash(hash(token)));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
