package com.cinescout.service;

import com.cinescout.domain.User;
import com.cinescout.dto.RegisterRequest;
import com.cinescout.dto.UserResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final BlockingTransactions db;

    public UserService(UserRepository users, PasswordEncoder encoder, BlockingTransactions db) {
        this.users = users;
        this.encoder = encoder;
        this.db = db;
    }

    /**
     * Creates a regular ({@code USER}) account; the role is never taken from the request. Emails are
     * unique ignoring case, enforced by the database, so a duplicate (even a racing one) is a
     * {@link ConflictException}. The password is hashed before the transaction opens: bcrypt is
     * deliberately slow and must not hold a database connection while it runs.
     */
    public Mono<UserResponse> register(RegisterRequest request) {
        return Mono.fromCallable(() -> encoder.encode(request.password()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hash -> db.call(() -> UserResponse.from(users.saveAndFlush(
                        new User(request.email().strip(), hash, request.displayName().strip())))))
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    public Mono<UserResponse> get(UUID userId) {
        return db.call(() -> UserResponse.from(users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId))));
    }

    public Mono<UserResponse> rename(UUID userId, String displayName) {
        return db.call(() -> {
            User user = existing(userId);
            user.setDisplayName(displayName.strip());
            return UserResponse.from(users.saveAndFlush(user));
        });
    }

    /**
     * Replaces the password, once {@code currentPassword} is shown to be the account's.
     *
     * @throws InvalidRequestException (as an error signal) naming {@code currentPassword} if it is not
     */
    public Mono<Void> changePassword(UUID userId, String currentPassword, String newPassword) {
        return verify(userId, "currentPassword", currentPassword)
                .then(Mono.fromCallable(() -> encoder.encode(newPassword)).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(hash -> db.run(() -> {
                    User user = existing(userId);
                    user.setPasswordHash(hash);
                    users.saveAndFlush(user);
                }));
    }

    /**
     * Deletes the account and, through the database's cascades, everything it owns: projects, scenes, locations,
     * outreach drafts and logins.
     *
     * @throws InvalidRequestException (as an error signal) naming {@code password} if it is not the account's
     */
    public Mono<Void> delete(UUID userId, String password) {
        return verify(userId, "password", password).then(db.run(() -> users.delete(existing(userId))));
    }

    /** Completes if {@code password} is the account's. The check is a bcrypt hash: slow, so off the database's time. */
    private Mono<Void> verify(UUID userId, String field, String password) {
        return db.call(() -> existing(userId).getPasswordHash())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(hash -> encoder.matches(password, hash)
                        ? Mono.<Void>empty()
                        : Mono.error(new InvalidRequestException(field, "That is not the account's password")));
    }

    private User existing(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
