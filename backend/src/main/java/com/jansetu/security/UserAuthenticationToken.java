package com.jansetu.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication carrying an {@link AuthenticatedUser}. Authorities = permission codes plus ROLE_ prefixed roles. */
public final class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;

    public UserAuthenticationToken(AuthenticatedUser user) {
        super(authorities(user));
        this.user = user;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> authorities(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.id() == null ? "system" : user.id().toString();
    }
}
