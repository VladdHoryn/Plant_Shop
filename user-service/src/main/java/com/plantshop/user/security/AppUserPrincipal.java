package com.plantshop.user.security;

import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The authenticated caller. Access decisions compare {@link #getId()} with the subject id, never the URL alone. */
@Getter
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final UserRole role;
    private String passwordHash;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
    }

    public boolean isSubject(Long subjectId) {
        return id.equals(subjectId);
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

    /** Called by Spring Security after authentication, so the hash does not live on in the security context. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", role=" + role + "]";
    }
}
