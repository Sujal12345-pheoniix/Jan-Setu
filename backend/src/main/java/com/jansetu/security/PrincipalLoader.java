package com.jansetu.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port implemented by the identity module: loads the current authorities of an <em>active</em> user.
 * Keeps the security infrastructure independent of business modules.
 */
public interface PrincipalLoader {

    record LoadedPrincipal(AuthenticatedUser user, Instant passwordChangedAt) {
    }

    /** Empty if the user does not exist or is not active. */
    Optional<LoadedPrincipal> loadActive(UUID userId);
}
