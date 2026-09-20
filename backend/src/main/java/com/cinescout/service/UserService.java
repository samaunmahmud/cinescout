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
}
