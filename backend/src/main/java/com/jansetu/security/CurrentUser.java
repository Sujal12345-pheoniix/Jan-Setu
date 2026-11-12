package com.jansetu.security;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the authenticated actor from the security context. */
@Component
public class CurrentUser {

    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));
    }

    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
