package com.cinescout.security;

import com.cinescout.domain.User;
import com.cinescout.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The logged-in account as Spring Security sees it: the principal controllers receive, carrying the
 * id every service scopes its queries by. Deliberately not the {@link User} entity, which must never
 * leave the service layer.
 */
public final class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;

    private AuthenticatedUser(UUID id, String email, String passwordHash, UserRole role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isEnabled());
    }

    public UUID id() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Never includes the password hash. */
    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
