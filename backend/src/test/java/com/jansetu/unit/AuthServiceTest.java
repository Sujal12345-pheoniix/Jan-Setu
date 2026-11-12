package com.jansetu.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.common.web.ClientInfo;
import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.domain.UserStatus;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.modules.identity.service.AuthService;
import com.jansetu.modules.identity.service.PasswordPolicy;
import com.jansetu.modules.identity.service.RefreshSessionService;
import com.jansetu.security.AccessTokenService;
import com.jansetu.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshSessionService refreshSessions;
    @Mock
    private AccessTokenService accessTokens;
    @Mock
    private RoleAssignmentService roleAssignments;
    @Mock
    private AuditService auditService;

    private AuthService authService;
    private final ClientInfo client = new ClientInfo("127.0.0.1", "JUnit");

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt("iss", "aud", Duration.ofMinutes(15), null, null, true),
                new SecurityProperties.Refresh(Duration.ofDays(7), "jansetu_rt", "/api/v1/auth", false),
                new SecurityProperties.PasswordReset(Duration.ofMinutes(30), "http://localhost"),
                new SecurityProperties.LoginProtection(5, Duration.ofMinutes(15)),
                new SecurityProperties.RateLimit(true, 20),
                new SecurityProperties.Cors(List.of("http://localhost:5173")));
        authService = new AuthService(userRepository, passwordEncoder, new PasswordPolicy(), refreshSessions,
                accessTokens, roleAssignments, auditService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user() {
        return new User("asha@example.test", "{bcrypt}hash", "Asha", null, UserType.CITIZEN, NOW.minusSeconds(3600), false);
    }

    @Test
    void unknownAccountStillPerformsAHashComparisonAndFailsGenerically() {
        when(userRepository.findByEmailNormalized("ghost@example.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login("Ghost@Example.test", "whatever", client))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches(eq("whatever"), anyString());
        verify(refreshSessions, never()).startFamily(any(), any());
    }

    @Test
    void fifthFailedAttemptLocksTheAccount() {
        User user = user();
        when(userRepository.findByEmailNormalized("asha@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "{bcrypt}hash")).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login("asha@example.test", "bad", client)).isInstanceOf(ApiException.class);
        }
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEvent::action).contains("ACCOUNT_LOCKED");
    }

    @Test
    void lockedAccountRejectsEvenCorrectPassword() {
        User user = user();
        user.recordFailedLogin(NOW, 1, Duration.ofMinutes(15));
        when(userRepository.findByEmailNormalized("asha@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "{bcrypt}hash")).thenReturn(true);
        assertThatThrownBy(() -> authService.login("asha@example.test", "correct", client))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshSessions, never()).startFamily(any(), any());
    }

    @Test
    void suspendedAccountWithCorrectPasswordIsDisabled() {
        User user = user();
        user.changeStatus(UserStatus.SUSPENDED, "test");
        when(userRepository.findByEmailNormalized("asha@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "{bcrypt}hash")).thenReturn(true);
        assertThatThrownBy(() -> authService.login("asha@example.test", "correct", client))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void reuseOfRotatedRefreshTokenRevokesFamily() {
        UUID family = UUID.randomUUID();
        RefreshSession rotated = new RefreshSession(UUID.randomUUID(), family, "hash", NOW.minusSeconds(60),
                NOW.plus(Duration.ofDays(7)), "ua", "ip");
        rotated.markRotated(UUID.randomUUID(), NOW.minusSeconds(30));
        when(refreshSessions.lockByRawToken("stolen")).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> authService.refresh("stolen", client))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshSessions).revokeFamily(family, RefreshSessionService.REASON_REUSE);
        verify(accessTokens, never()).issue(any());
    }

    @Test
    void expiredRefreshSessionIsRejected() {
        RefreshSession expired = new RefreshSession(UUID.randomUUID(), UUID.randomUUID(), "hash",
                NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofDays(1)), "ua", "ip");
        when(refreshSessions.lockByRawToken("old")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> authService.refresh("old", client))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshSessions, never()).rotate(any(), any());
    }
}
