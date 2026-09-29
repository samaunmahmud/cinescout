package com.cinescout.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PasswordAuthenticationTest {

    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4)); // the lowest cost, to keep the test fast
    private final String adaHash = encoder.encode("correct horse");
    private final Map<String, UserDetails> accounts = Map.of(
            "ada@example.com", User.withUsername("ada@example.com").password(adaHash).roles("USER").build(),
            "bob@example.com", User.withUsername("bob@example.com").password(adaHash).roles("USER").disabled(true).build());
    private final ReactiveUserDetailsService users = email -> Mono.justOrEmpty(accounts.get(email));
    private final PasswordAuthentication authentication = new PasswordAuthentication(users, encoder);

    private Mono<Authentication> login(String email, String password) {
        return authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    }

    @Test
    void theRightPasswordLogsInWithoutKeepingTheHash() {
        Authentication result = login("ada@example.com", "correct horse").block();

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(((UserDetails) result.getPrincipal()).getUsername()).isEqualTo("ada@example.com");
        assertThat(result.getCredentials()).isNull();
    }

    @Test
    void anUnknownEmailIsHashedLikeAKnownOneAndFailsTheSameWay() {
        assertThatThrownBy(() -> login("nobody@example.com", "correct horse").block()).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> login("ada@example.com", "wrong").block()).isInstanceOf(BadCredentialsException.class);

        verify(encoder).matches(eq("correct horse"), anyString()); // the unknown account's stand-in hash
        verify(encoder).matches("wrong", adaHash);
    }

    @Test
    void aDisabledAccountLooksLikeAnyOtherToAWrongGuess() {
        assertThatThrownBy(() -> login("bob@example.com", "wrong").block()).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> login("bob@example.com", "correct horse").block()).isInstanceOf(DisabledException.class);

        verify(encoder, times(2)).matches(anyString(), eq(adaHash));
    }

    @Test
    void aMissingPasswordIsAWrongOne() {
        assertThatThrownBy(() -> authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("ada@example.com", null)).block())
                .isInstanceOf(BadCredentialsException.class);
    }
}
