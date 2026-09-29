package com.cinescout.security;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.repository.AuthSessionRepository;
import com.cinescout.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * Everything requires a login except registering, logging in and out, and the API description. There are
 * two ways to authenticate, both against the {@code users} table:
 * <ul>
 *   <li>a session cookie, which the web app gets from {@code POST /api/auth/login} (see {@link SessionCookies}
 *       for how it is kept safe from CSRF);</li>
 *   <li>HTTP Basic on every request, for scripts and API tools.</li>
 * </ul>
 * Spring's own CSRF protection stays off: Basic credentials are only sent when a client sets them, and the
 * cookie only counts together with a header another site cannot send. No server-side security context is
 * kept between requests. Controllers just receive an {@link AuthenticatedUser}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    /** Delegating encoder: hashes are stored as {@code {bcrypt}...}, so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    ReactiveUserDetailsService userDetailsService(UserRepository users, BlockingTransactions db) {
        // An unknown email yields an empty Mono, which Spring Security treats as "no such user".
        return email -> db.call(() -> users.findByEmailIgnoreCase(email).map(AuthenticatedUser::from).orElse(null))
                .map(user -> (org.springframework.security.core.userdetails.UserDetails) user);
    }

    /**
     * Checks an email and password; HTTP Basic and the login endpoint both use it. Failures are limited per client
     * address ({@link ThrottledAuthentication}), which {@link ClientAddress} makes known to it.
     */
    @Bean
    ReactiveAuthenticationManager passwordAuthentication(ReactiveUserDetailsService userDetails, PasswordEncoder encoder,
                                                         RateLimiter limits) {
        UserDetailsRepositoryReactiveAuthenticationManager manager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetails);
        manager.setPasswordEncoder(encoder);
        return new ThrottledAuthentication(manager, limits);
    }

    /** Runs before Spring Security's filters, so the address is known to the password check. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ClientAddress clientAddress() {
        return new ClientAddress();
    }

    @Bean
    SessionService sessionService(AuthSessionRepository sessions, UserRepository users, BlockingTransactions db, SecurityProperties props) {
        return new SessionService(sessions, users, db, props.sessionTtl(), DatabaseTime.clock());
    }

    @Bean
    SessionCookies sessionCookies(SecurityProperties props) {
        return new SessionCookies(props.cookieSecure());
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper mapper, SessionService sessions) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .addFilterAt(sessionAuthentication(sessions), SecurityWebFiltersOrder.AUTHENTICATION)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .httpBasic(basic -> basic.authenticationEntryPoint(new ProblemAuthenticationEntryPoint(mapper)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new ProblemAuthenticationEntryPoint(mapper)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        // The API description and Swagger UI. Turn them off with springdoc.api-docs.enabled=false.
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                        // Up or down, nothing more (show-details: never): for the container health check.
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyExchange().authenticated())
                .build();
    }

    /**
     * Logs a request in by its session cookie. An unknown or expired session is simply no login: the request
     * goes on anonymously, so the login page still works with a stale cookie, and anything else gets the 401.
     */
    private static AuthenticationWebFilter sessionAuthentication(SessionService sessions) {
        AuthenticationWebFilter filter = new AuthenticationWebFilter((ReactiveAuthenticationManager) Mono::just);
        filter.setServerAuthenticationConverter(exchange -> Mono.justOrEmpty(SessionCookies.token(exchange.getRequest()))
                .flatMap(sessions::resolve)
                .map(user -> UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())));
        filter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return filter;
    }
}
