package com.cinescout.web;

import com.cinescout.dto.LoginRequest;
import com.cinescout.dto.RegisterRequest;
import com.cinescout.dto.UserResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.security.SecurityProperties;
import com.cinescout.security.SessionCookies;
import com.cinescout.security.SessionService;
import com.cinescout.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Accounts and web app logins. Every other endpoint needs a login: "
        + "HTTP Basic credentials, or the session cookie from logging in.")
class AuthController {

    private final UserService users;
    private final SecurityProperties security;
    private final ReactiveAuthenticationManager passwords;
    private final SessionService sessions;
    private final SessionCookies cookies;

    AuthController(UserService users, SecurityProperties security, ReactiveAuthenticationManager passwords,
                   SessionService sessions, SessionCookies cookies) {
        this.users = users;
        this.security = security;
        this.passwords = passwords;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    /** Creates an account. Public; log in afterwards, or send HTTP Basic credentials. */
    @Operation(summary = "Register an account",
            description = "Creates a regular account. Then log in for a session cookie, or send the email and password as HTTP Basic credentials.")
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!security.registrationOpen()) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is closed"));
        }
        return users.register(request);
    }

    /**
     * Starts a web app session: sets the session cookie and returns the account. A wrong password, an unknown
     * email and a disabled account get the same 401.
     */
    @Operation(summary = "Log in",
            description = "Sets an HttpOnly session cookie. It counts only on requests that also send "
                    + "`X-Requested-With: XMLHttpRequest`, which protects it from cross-site requests.")
    @SecurityRequirements
    @PostMapping("/login")
    Mono<UserResponse> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return passwords.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email().strip(), request.password()))
                .onErrorMap(AuthenticationException.class,
                        e -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The email or password is not right"))
                .map(authentication -> ((AuthenticatedUser) authentication.getPrincipal()).id())
                .flatMap(userId -> sessions.open(userId)
                        .doOnNext(token -> exchange.getResponse().addCookie(cookies.issue(exchange, token, sessions.ttl())))
                        .then(users.get(userId)));
    }

    /** Ends the web app session, if there is one, and clears the cookie. */
    @Operation(summary = "Log out")
    @SecurityRequirements
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> logout(ServerWebExchange exchange) {
        return sessions.close(SessionCookies.token(exchange.getRequest()))
                .then(Mono.fromRunnable(() -> exchange.getResponse().addCookie(cookies.clear(exchange))));
    }

    /** The account the login belongs to. */
    @Operation(summary = "The current account")
    @GetMapping("/me")
    Mono<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return users.get(principal.id());
    }
}
