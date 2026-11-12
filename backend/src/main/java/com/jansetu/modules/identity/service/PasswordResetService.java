package com.jansetu.modules.identity.service;

import com.jansetu.common.crypto.SecureTokens;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.integration.email.EmailDeliveryPort;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.identity.domain.PasswordResetToken;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.repository.PasswordResetTokenRepository;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.SecurityProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Password reset. Requests always return the same response whether or not the account exists (no account
 * enumeration). Tokens are random, stored hashed, valid for 30 minutes and single use; earlier outstanding tokens
 * are invalidated when a new one is issued, and a completed reset revokes all refresh sessions.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshSessionService refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailDeliveryPort email;
    private final AuditService auditService;
    private final SecurityProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                RefreshSessionService refreshSessions, PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy, EmailDeliveryPort email, AuditService auditService,
                                SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.email = email;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String emailAddress) {
        userRepository.findByEmailNormalized(User.normalizeEmail(emailAddress))
                .filter(User::isActive)
                .ifPresent(this::issueToken);
    }

    private void issueToken(User user) {
        Instant now = clock.instant();
        tokenRepository.invalidateOutstanding(user.getId(), now);
        String raw = SecureTokens.newToken();
        tokenRepository.save(new PasswordResetToken(user.getId(), SecureTokens.sha256(raw), now,
                now.plus(properties.passwordReset().ttl())));
        String link = properties.passwordReset().resetUrlBase() + "?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
        sendAfterCommit(new EmailDeliveryPort.EmailMessage(user.getEmail(), "JanSetu (demo) password reset",
                """
                A password reset was requested for your JanSetu demo account.

                Open this link within %d minutes to choose a new password:
                %s

                If you did not request this, ignore this message. JanSetu is a portfolio simulation, not a government service.
                """.formatted(properties.passwordReset().ttl().toMinutes(), link)));
        auditService.record(AuditEvent.success(AuditActions.PASSWORD_RESET_REQUESTED, "USER", user.getId())
                .actor(user.getId()));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(SecureTokens.sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(PasswordResetService::invalidToken);
        passwordPolicy.validate(newPassword, user.getEmailNormalized());
        token.markUsed(now);
        user.changePassword(passwordEncoder.encode(newPassword), now, false);
        userRepository.saveAndFlush(user);
        refreshSessions.revokeAll(user.getId(), RefreshSessionService.REASON_PASSWORD_CHANGED);
        auditService.record(AuditEvent.success(AuditActions.PASSWORD_RESET_COMPLETED, "USER", user.getId())
                .actor(user.getId()));
    }

    /** External side effects happen only after the token is durably stored. */
    private void sendAfterCommit(EmailDeliveryPort.EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    email.send(message);
                }
            });
        } else {
            email.send(message);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(ErrorCode.INVALID_RESET_TOKEN, "The reset link is invalid or has expired. Request a new one.");
    }
}
