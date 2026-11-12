package com.jansetu.modules.identity.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.common.web.ClientInfo;
import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.domain.RefreshSessionStatus;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.AccessTokenService;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh-token rotation and logout.
 *
 * <p>Login and refresh use {@code noRollbackFor = ApiException.class}: failed-attempt counters, lockouts, family
 * revocations and their audit records must be committed even though the request fails.
 */
@Service
public class AuthService {

    /** Opaque result of a successful login/refresh. The refresh token is set as an HttpOnly cookie by the controller. */
    public record AuthResult(UUID userId, AccessTokenService.IssuedToken accessToken, String refreshToken,
                             Instant refreshExpiresAt) {
    }

    public record RegisterCommand(String email, String password, String fullName, String phone) {
    }

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshSessionService refreshSessions;
    private final AccessTokenService accessTokens;
    private final RoleAssignmentService roleAssignments;
    private final AuditService auditService;
    private final SecurityProperties properties;
    private final Clock clock;
    /** Hash of a random value, used to equalise timing for unknown accounts. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       RefreshSessionService refreshSessions, AccessTokenService accessTokens,
                       RoleAssignmentService roleAssignments, AuditService auditService,
                       SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = refreshSessions;
        this.accessTokens = accessTokens;
        this.roleAssignments = roleAssignments;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserView register(RegisterCommand command) {
        String normalized = User.normalizeEmail(command.email());
        passwordPolicy.validate(command.password(), normalized);
        if (userRepository.existsByEmailNormalized(normalized)) {
            throw ApiException.duplicate("An account with this email already exists.");
        }
        User user = userRepository.saveAndFlush(new User(command.email(), passwordEncoder.encode(command.password()),
                command.fullName(), blankToNull(command.phone()), UserType.CITIZEN, clock.instant(), false));
        Set<String> roles = roleAssignments.replaceRoles(user.getId(), Set.of("CITIZEN"),
                AuthenticatedUser.system(Set.of()));
        auditService.record(AuditEvent.success(AuditActions.USER_REGISTERED, "USER", user.getId()).actor(user.getId()));
        return UserView.of(user, roles, Set.of());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String email, String password, ClientInfo client) {
        Instant now = clock.instant();
        Optional<User> found = userRepository.findByEmailNormalized(User.normalizeEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            auditService.record(AuditEvent.failure(AuditActions.LOGIN_FAILED, "USER", null)
                    .detail("reason", "UNKNOWN_ACCOUNT"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            auditService.record(AuditEvent.denied(AuditActions.LOGIN_FAILED, "USER", user.getId())
                    .detail("reason", "LOCKED"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            var protection = properties.loginProtection();
            boolean locked = user.recordFailedLogin(now, protection.maxFailedAttempts(), protection.lockDuration());
            auditService.record(AuditEvent.failure(AuditActions.LOGIN_FAILED, "USER", user.getId())
                    .detail("reason", "BAD_PASSWORD"));
            if (locked) {
                auditService.record(AuditEvent.success(AuditActions.ACCOUNT_LOCKED, "USER", user.getId())
                        .detail("lockedUntil", user.getLockedUntil().toString()));
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            auditService.record(AuditEvent.denied(AuditActions.LOGIN_FAILED, "USER", user.getId())
                    .detail("reason", "ACCOUNT_" + user.getStatus().name()));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is not active. Contact support.");
        }
        user.recordSuccessfulLogin(now);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.upgradePasswordHash(passwordEncoder.encode(password));
        }
        RefreshSessionService.IssuedRefresh refresh = refreshSessions.startFamily(user.getId(), client);
        auditService.record(AuditEvent.success(AuditActions.LOGIN_SUCCEEDED, "USER", user.getId()).actor(user.getId()));
        return new AuthResult(user.getId(), accessTokens.issue(user.getId()), refresh.rawToken(),
                refresh.session().getExpiresAt());
    }

    /**
     * Rotates a refresh token. Reuse of an already-rotated token revokes the whole session family (likely theft).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefresh();
        }
        Instant now = clock.instant();
        RefreshSession session = refreshSessions.lockByRawToken(rawRefreshToken).orElseThrow(AuthService::invalidRefresh);
        if (session.getStatus() == RefreshSessionStatus.ROTATED) {
            int revoked = refreshSessions.revokeFamily(session.getFamilyId(), RefreshSessionService.REASON_REUSE);
            auditService.record(AuditEvent.denied(AuditActions.REFRESH_TOKEN_REUSE_DETECTED, "USER", session.getUserId())
                    .detail("familyId", session.getFamilyId().toString())
                    .detail("revokedSessions", revoked));
            throw invalidRefresh();
        }
        if (session.getStatus() != RefreshSessionStatus.ACTIVE) {
            throw invalidRefresh();
        }
        if (session.isExpired(now)) {
            session.revoke(RefreshSessionService.REASON_EXPIRED, now);
            throw invalidRefresh();
        }
        User user = userRepository.findById(session.getUserId()).orElseThrow(AuthService::invalidRefresh);
        if (!user.isActive()) {
            refreshSessions.revokeAll(user.getId(), RefreshSessionService.REASON_ACCOUNT_STATUS);
            throw invalidRefresh();
        }
        RefreshSessionService.IssuedRefresh next = refreshSessions.rotate(session, client);
        auditService.record(AuditEvent.success(AuditActions.TOKEN_REFRESHED, "USER", user.getId()).actor(user.getId()));
        return new AuthResult(user.getId(), accessTokens.issue(user.getId()), next.rawToken(),
                next.session().getExpiresAt());
    }

    /** Revokes the session family of the presented refresh token. Idempotent: unknown tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshSessions.lockByRawToken(rawRefreshToken).ifPresent(session -> {
            int revoked = refreshSessions.revokeFamily(session.getFamilyId(), RefreshSessionService.REASON_LOGOUT);
            if (revoked > 0) {
                auditService.record(AuditEvent.success(AuditActions.LOGGED_OUT, "USER", session.getUserId())
                        .actor(session.getUserId()));
            }
        });
    }

    @Transactional
    public int logoutEverywhere(AuthenticatedUser actor) {
        int revoked = refreshSessions.revokeAll(actor.id(), RefreshSessionService.REASON_LOGOUT_ALL);
        auditService.record(AuditEvent.success(AuditActions.SESSIONS_REVOKED, "USER", actor.id())
                .detail("revokedSessions", revoked));
        return revoked;
    }

    @Transactional
    public void changePassword(AuthenticatedUser actor, String currentPassword, String newPassword) {
        User user = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            auditService.record(AuditEvent.failure(AuditActions.PASSWORD_CHANGED, "USER", user.getId())
                    .detail("reason", "BAD_CURRENT_PASSWORD"));
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The new password must differ from the current one.");
        }
        passwordPolicy.validate(newPassword, user.getEmailNormalized());
        user.changePassword(passwordEncoder.encode(newPassword), clock.instant(), false);
        userRepository.saveAndFlush(user);
        refreshSessions.revokeAll(user.getId(), RefreshSessionService.REASON_PASSWORD_CHANGED);
        auditService.record(AuditEvent.success(AuditActions.PASSWORD_CHANGED, "USER", user.getId()));
    }

    private static ApiException invalidRefresh() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "The refresh session is invalid or has expired. Sign in again.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
