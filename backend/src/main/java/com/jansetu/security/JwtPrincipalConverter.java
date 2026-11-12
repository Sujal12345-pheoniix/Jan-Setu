package com.jansetu.security;

import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Converts a validated JWT into a {@link UserAuthenticationToken}. The token only identifies the user; status,
 * roles, permissions and department scope are loaded fresh, and tokens issued before the last password change are
 * rejected.
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalLoader principalLoader;

    public JwtPrincipalConverter(PrincipalLoader principalLoader) {
        this.principalLoader = principalLoader;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        PrincipalLoader.LoadedPrincipal loaded = principalLoader.loadActive(userId)
                .orElseThrow(() -> new DisabledException("Account is not active"));
        if (jwt.getIssuedAt() != null
                && jwt.getIssuedAt().isBefore(loaded.passwordChangedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new InvalidBearerTokenException("Token issued before the last password change");
        }
        return new UserAuthenticationToken(loaded.user());
    }
}
