package com.cinescout.web;

import com.cinescout.dto.ChangePasswordRequest;
import com.cinescout.dto.DeleteAccountRequest;
import com.cinescout.dto.UpdateAccountRequest;
import com.cinescout.dto.UserResponse;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.security.ClientAddress;
import com.cinescout.security.SessionCookies;
import com.cinescout.security.SecurityProperties;
import com.cinescout.security.SessionService;
import com.cinescout.service.ForbiddenException;
import com.cinescout.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * What a user can do to their own account. The two operations that take the password again count wrong
 * passwords against the same per-address limit as failed logins: a stolen session must not become a way to
 * guess the password without limit. A shared account ({@code cinescout.security.locked-accounts}, the public demo login)
 * keeps its name and password and cannot be deleted: one visitor must not lock the others out.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "The caller's own account: its name, its password, and deleting it.")
class AccountController {

    private final UserService users;
    private final SessionService sessions;
    private final SessionCookies cookies;
    private final RateLimiter limits;
    private final SecurityProperties security;

    AccountController(UserService users, SessionService sessions, SessionCookies cookies, RateLimiter limits,
                      SecurityProperties security) {
        this.users = users;
        this.sessions = sessions;
        this.cookies = cookies;
        this.limits = limits;
        this.security = security;
    }

    @Operation(summary = "Change the account's display name",
            description = "The name outreach emails are signed with. The email address is the login and cannot be changed.")
    @ApiResponse(responseCode = "403", description = "A shared demo account, which stays as it is")
    @PutMapping
    Mono<UserResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateAccountRequest request) {
        return unlocked(user).then(Mono.defer(() -> users.rename(user.id(), request.displayName())));
    }

    /** Changes the password and logs the account out everywhere else; the session making the request stays. */
    @Operation(summary = "Change the account's password",
            description = "Needs the current password (400 on `currentPassword` when it is wrong). Every other session of the account is ended; "
                    + "the one making the request stays logged in.")
    @ApiResponse(responseCode = "429", description = "Too many wrong passwords from this address lately; see Retry-After")
    @ApiResponse(responseCode = "403", description = "A shared demo account, which stays as it is")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ChangePasswordRequest request,
                              ServerWebExchange exchange) {
        return unlocked(user).then(Mono.defer(() -> passwordChecked(exchange, users.changePassword(user.id(), request.currentPassword(), request.newPassword()))
                .then(Mono.defer(() -> sessions.closeOthers(user.id(), SessionCookies.token(exchange.getRequest()))))));
    }

    /** Deletes the account with everything it owns, and clears the session cookie. */
    @Operation(summary = "Delete the account",
            description = "Needs the password (400 on `password` when it is wrong). Deletes the account with its projects, scenes, locations "
                    + "and outreach drafts, and ends all its sessions. It cannot be undone. A POST, as it carries the password in its body.")
    @ApiResponse(responseCode = "204", description = "The account is gone")
    @ApiResponse(responseCode = "429", description = "Too many wrong passwords from this address lately; see Retry-After")
    @ApiResponse(responseCode = "403", description = "A shared demo account, which stays as it is")
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DeleteAccountRequest request,
                      ServerWebExchange exchange) {
        return unlocked(user).then(Mono.defer(() -> passwordChecked(exchange, users.delete(user.id(), request.password()))
                .then(Mono.fromRunnable(() -> exchange.getResponse().addCookie(cookies.clear(exchange))))));
    }

    /** Refuses (403) before anything else happens, so a locked account's password is not even checked. */
    private Mono<Void> unlocked(AuthenticatedUser user) {
        return security.locked(user.getUsername())
                ? Mono.error(new ForbiddenException("This is a shared demo account: its name and password stay as they are, and it cannot be deleted."))
                : Mono.empty();
    }

    /** Takes one attempt from the address's login allowance up front and gives it back when the password was right. */
    private Mono<Void> passwordChecked(ServerWebExchange exchange, Mono<Void> work) {
        String client = ClientAddress.of(exchange);
        return limits.acquire(RateLimit.LOGIN, client)
                .then(work)
                .then(Mono.fromRunnable(() -> limits.refund(RateLimit.LOGIN, client)));
    }
}
