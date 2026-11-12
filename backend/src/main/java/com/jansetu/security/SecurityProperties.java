package com.jansetu.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jansetu.security")
public record SecurityProperties(
        Jwt jwt,
        Refresh refresh,
        PasswordReset passwordReset,
        LoginProtection loginProtection,
        RateLimit rateLimit,
        Cors cors) {

    public record Jwt(String issuer, String audience, Duration accessTokenTtl, String privateKey, String publicKey,
                      boolean allowEphemeralKeys) {
    }

    public record Refresh(Duration ttl, String cookieName, String cookiePath, boolean cookieSecure) {
    }

    public record PasswordReset(Duration ttl, String resetUrlBase) {
    }

    public record LoginProtection(int maxFailedAttempts, Duration lockDuration) {
    }

    public record RateLimit(boolean enabled, int authRequestsPerMinute) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
