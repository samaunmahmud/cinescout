package com.cinescout.web;

import com.cinescout.dto.RegisterRequest;
import com.cinescout.dto.UserResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.security.SecurityProperties;
import com.cinescout.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Accounts. Every other endpoint needs HTTP Basic credentials.")
class AuthController {

    private final UserService users;
    private final SecurityProperties security;

    AuthController(UserService users, SecurityProperties security) {
        this.users = users;
        this.security = security;
    }

    /** Creates an account. Public; there is no login step, clients then send HTTP Basic credentials. */
    @Operation(summary = "Register an account",
            description = "Creates a regular account. There is no login step: send the email and password as HTTP Basic credentials afterwards.")
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!security.registrationOpen()) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is closed"));
        }
        return users.register(request);
    }

    /** The account the credentials belong to. */
    @Operation(summary = "The current account")
    @GetMapping("/me")
    Mono<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return users.get(principal.id());
    }
}
