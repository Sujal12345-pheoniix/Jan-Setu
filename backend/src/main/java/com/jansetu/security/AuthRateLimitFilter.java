package com.jansetu.security;

import com.jansetu.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client fixed-window rate limit for credential endpoints (login, registration, refresh, password reset).
 * Complements per-account lockout against credential stuffing.
 *
 * <p>Limitation: counters are held in memory, so limits apply per application instance. Move the counters to Redis
 * before running multiple replicas (see docs/SECURITY.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private record Window(long startedAt, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final SecurityProperties properties;
    private final ProblemResponseWriter writer;
    private final Clock clock;

    public AuthRateLimitFilter(SecurityProperties properties, ProblemResponseWriter writer, Clock clock) {
        this.properties = properties;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.rateLimit().enabled()
                || !"POST".equals(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= WINDOW_MILLIS);
        }
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Window window = windows.compute(key, (k, current) ->
                current == null || now - current.startedAt() >= WINDOW_MILLIS
                        ? new Window(now, new AtomicInteger())
                        : current);
        if (window.count().incrementAndGet() > properties.rateLimit().authRequestsPerMinute()) {
            long retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - (now - window.startedAt())) / 1000);
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            writer.write(request, response, ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
