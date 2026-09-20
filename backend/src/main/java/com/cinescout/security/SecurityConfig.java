package com.cinescout.security;

import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Everything requires a login except registration. Authentication is HTTP Basic against the
 * {@code users} table: stateless, no session and no cookies. That is also why CSRF protection is
 * off: CSRF works by riding on credentials a browser attaches on its own, and Basic credentials
 * are only sent when the client sets them. Swapping Basic for tokens later changes this class only;
 * controllers just receive an {@link AuthenticatedUser}.
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

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper mapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .httpBasic(basic -> basic.authenticationEntryPoint(new ProblemAuthenticationEntryPoint(mapper)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new ProblemAuthenticationEntryPoint(mapper)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .anyExchange().authenticated())
                .build();
    }
}
