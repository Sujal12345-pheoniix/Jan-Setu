package com.jansetu.modules.identity.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.common.web.ClientInfo;
import com.jansetu.modules.identity.api.IdentityDtos.ChangePasswordRequest;
import com.jansetu.modules.identity.api.IdentityDtos.CurrentUserResponse;
import com.jansetu.modules.identity.api.IdentityDtos.ForgotPasswordRequest;
import com.jansetu.modules.identity.api.IdentityDtos.LoginRequest;
import com.jansetu.modules.identity.api.IdentityDtos.MessageResponse;
import com.jansetu.modules.identity.api.IdentityDtos.RegisterRequest;
import com.jansetu.modules.identity.api.IdentityDtos.ResetPasswordRequest;
import com.jansetu.modules.identity.api.IdentityDtos.SessionResponse;
import com.jansetu.modules.identity.api.IdentityDtos.TokenResponse;
import com.jansetu.modules.identity.api.IdentityDtos.UpdateProfileRequest;
import com.jansetu.modules.identity.api.IdentityDtos.UserResponse;
import com.jansetu.modules.identity.service.AccountService;
import com.jansetu.modules.identity.service.AuthService;
import com.jansetu.modules.identity.service.AuthService.AuthResult;
import com.jansetu.modules.identity.service.AuthService.RegisterCommand;
import com.jansetu.modules.identity.service.PasswordResetService;
import com.jansetu.modules.identity.service.RefreshSessionService;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.CurrentUser;
import com.jansetu.security.SecurityConfig;
import com.jansetu.security.SecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token refresh, logout, password reset, current user")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AccountService accountService;
    private final RefreshSessionService refreshSessionService;
    private final CurrentUser currentUser;
    private final SecurityProperties properties;
    private final Clock clock;
    private final com.jansetu.modules.audit.service.AuditQueryService auditQueryService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
                          AccountService accountService, RefreshSessionService refreshSessionService,
                          CurrentUser currentUser, SecurityProperties properties, Clock clock,
                          com.jansetu.modules.audit.service.AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.accountService = accountService;
        this.refreshSessionService = refreshSessionService;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a citizen account", description = "Rate limited. Use synthetic data only.")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(UserResponse.from(authService.register(new RegisterCommand(request.email(),
                request.password(), request.fullName(), request.phone()))));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a short-lived access token and sets the refresh-token cookie. "
            + "Accounts lock for 15 minutes after 5 failed attempts. Errors are deliberately generic.")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest http) {
        return tokenResponse(authService.login(request.email(), request.password(), ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token",
            description = "Requires the refresh cookie and the X-JanSetu-Client header. Reusing an old refresh token revokes the whole session family.")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest http) {
        requireClientHeader(http);
        return tokenResponse(authService.refresh(refreshCookie(http), ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out this device", description = "Revokes the refresh session and clears the cookie. Requires X-JanSetu-Client.")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        requireClientHeader(http);
        authService.logout(refreshCookie(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Log out every device of the current user")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutEverywhere(currentUser.require());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset link", description = "Always returns 202, whether or not the account exists.")
    public ApiResponse<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.of(new MessageResponse("If an active account exists for this address, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a reset token", description = "Single use; revokes all sessions.")
    public ApiResponse<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.of(new MessageResponse("Your password has been changed. Sign in with the new password."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your password", description = "Revokes all refresh sessions and invalidates older access tokens.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.require(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, roles, departments and effective permissions")
    public ApiResponse<CurrentUserResponse> me() {
        AuthenticatedUser actor = currentUser.require();
        return ApiResponse.of(new CurrentUserResponse(UserResponse.from(accountService.me(actor)),
                new java.util.TreeSet<>(actor.permissions())));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update your own profile (name, phone)")
    public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.of(UserResponse.from(accountService.updateMe(currentUser.require(), request.fullName(),
                request.phone(), request.version())));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List your active device sessions")
    public ApiResponse<List<SessionResponse>> sessions(HttpServletRequest http) {
        UUID current = refreshSessionService.sessionIdOf(refreshCookie(http)).orElse(null);
        return ApiResponse.of(accountService.sessions(currentUser.require()).stream()
                .map(s -> SessionResponse.from(s, current))
                .toList());
    }

    public record ActivityItem(java.time.Instant occurredAt, String action, String outcome, String ipAddress) {
    }

    @GetMapping("/activity")
    @Operation(summary = "Your recent account activity (last 50 audit events you performed)")
    public ApiResponse<List<ActivityItem>> activity() {
        return ApiResponse.of(auditQueryService.recentForActor(currentUser.require().id(), 50).stream()
                .map(a -> new ActivityItem(a.getOccurredAt(), a.getAction(), a.getOutcome().name(), a.getIpAddress()))
                .toList());
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke one of your device sessions")
    public void revokeSession(@PathVariable UUID id) {
        accountService.revokeSession(currentUser.require(), id);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthResult result) {
        Duration maxAge = Duration.between(clock.instant(), result.refreshExpiresAt());
        ResponseCookie cookie = baseCookie(result.refreshToken()).maxAge(maxAge).build();
        TokenResponse body = new TokenResponse(result.accessToken().value(), "Bearer",
                result.accessToken().expiresInSeconds(), result.accessToken().expiresAt(), result.refreshExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(body));
    }

    private ResponseCookie clearCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.refresh().cookieName(), value)
                .httpOnly(true)
                .secure(properties.refresh().cookieSecure())
                .sameSite("Strict")
                .path(properties.refresh().cookiePath());
    }

    private String refreshCookie(HttpServletRequest http) {
        if (http.getCookies() == null) {
            return null;
        }
        for (var cookie : http.getCookies()) {
            if (cookie.getName().equals(properties.refresh().cookieName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** CSRF defence for the cookie-authenticated endpoints (see SecurityConfig). */
    private static void requireClientHeader(HttpServletRequest http) {
        String value = http.getHeader(SecurityConfig.CLIENT_HEADER);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.CLIENT_HEADER_REQUIRED,
                    "The " + SecurityConfig.CLIENT_HEADER + " header is required for this request.");
        }
    }
}
