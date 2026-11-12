package com.jansetu.modules.identity.service;

import com.jansetu.common.crypto.SecureTokens;
import com.jansetu.common.web.ClientInfo;
import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.domain.RefreshSessionStatus;
import com.jansetu.modules.identity.repository.RefreshSessionRepository;
import com.jansetu.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Creation, rotation and revocation of refresh sessions. Raw tokens leave this class only to be set as cookies. */
@Service
public class RefreshSessionService {

    public record IssuedRefresh(String rawToken, RefreshSession session) {
    }

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_LOGOUT_ALL = "LOGOUT_ALL";
    public static final String REASON_REUSE = "REUSE_DETECTED";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_ACCOUNT_STATUS = "ACCOUNT_STATUS_CHANGED";
    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_USER_REVOKED = "USER_REVOKED";

    private final RefreshSessionRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    public RefreshSessionService(RefreshSessionRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefresh startFamily(UUID userId, ClientInfo client) {
        return issue(userId, UUID.randomUUID(), client);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshSession> lockByRawToken(String rawToken) {
        return repository.findByTokenHashForUpdate(SecureTokens.sha256(rawToken));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefresh rotate(RefreshSession current, ClientInfo client) {
        IssuedRefresh next = issue(current.getUserId(), current.getFamilyId(), client);
        current.markRotated(next.session().getId(), clock.instant());
        return next;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeFamily(UUID familyId, String reason) {
        return repository.revokeFamily(familyId, reason, clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAll(UUID userId, String reason) {
        return repository.revokeAllForUser(userId, reason, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<RefreshSession> activeSessions(UUID userId) {
        return repository.findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(userId,
                RefreshSessionStatus.ACTIVE, clock.instant());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> sessionIdOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(SecureTokens.sha256(rawToken)).map(RefreshSession::getId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshSession> findOwned(UUID sessionId, UUID userId) {
        return repository.findById(sessionId).filter(s -> s.getUserId().equals(userId));
    }

    private IssuedRefresh issue(UUID userId, UUID familyId, ClientInfo client) {
        String raw = SecureTokens.newToken();
        Instant now = clock.instant();
        RefreshSession session = new RefreshSession(userId, familyId, SecureTokens.sha256(raw), now,
                now.plus(properties.refresh().ttl()), client.userAgent(), client.ipAddress());
        repository.saveAndFlush(session);
        return new IssuedRefresh(raw, session);
    }
}
