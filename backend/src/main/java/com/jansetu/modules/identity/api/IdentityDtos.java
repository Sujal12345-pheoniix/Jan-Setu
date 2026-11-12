package com.jansetu.modules.identity.api;

import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.service.StaffMembershipService.MemberView;
import com.jansetu.modules.identity.service.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Request and response DTOs of the identity module. Passwords are write-only and never returned. */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    static final String PHONE_REGEX = "^\\+?[0-9][0-9 -]{6,18}$";

    // ---- Auth ----------------------------------------------------------------------------------------------------

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String phone) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + "]";
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    /** Access token response. The refresh token is only ever sent as an HttpOnly cookie. */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
                                Instant refreshExpiresAt) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 64) String newPassword) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(max = 64) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record MessageResponse(String message) {
    }

    public record SessionResponse(UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt, String userAgent,
                                  String ipAddress, boolean current) {
        static SessionResponse from(RefreshSession s, UUID currentSessionId) {
            return new SessionResponse(s.getId(), s.getCreatedAt(), s.getLastUsedAt(), s.getExpiresAt(),
                    s.getUserAgent(), s.getIpAddress(), s.getId().equals(currentSessionId));
        }
    }

    // ---- Users ---------------------------------------------------------------------------------------------------

    public record UserResponse(UUID id, String email, String fullName, String phone, String userType, String status,
                               String statusReason, boolean passwordChangeRequired, Instant lastLoginAt,
                               Set<String> roles, Set<UUID> departmentIds, Instant createdAt, Instant updatedAt,
                               long version) {
        public static UserResponse from(UserView v) {
            return new UserResponse(v.id(), v.email(), v.fullName(), v.phone(), v.userType(), v.status(),
                    v.statusReason(), v.passwordChangeRequired(), v.lastLoginAt(), new TreeSet<>(v.roles()),
                    v.departmentIds(), v.createdAt(), v.updatedAt(), v.version());
        }
    }

    /** Current principal including effective permissions (for UI rendering; the server remains authoritative). */
    public record CurrentUserResponse(UserResponse user, Set<String> permissions) {
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 150) String fullName,
            @Pattern(regexp = "^$|" + PHONE_REGEX, message = "must be a valid phone number") String phone,
            @NotNull Long version) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "must be a valid phone number") String phone,
            @NotBlank @Size(max = 64) String initialPassword,
            @NotEmpty @Size(max = 10) Set<@NotBlank String> roles) {
        @Override
        public String toString() {
            return "CreateUserRequest[email=" + email + "]";
        }
    }

    public record UpdateUserRequest(
            @Size(min = 2, max = 150) String fullName,
            @Pattern(regexp = "^$|" + PHONE_REGEX, message = "must be a valid phone number") String phone,
            @NotNull Long version) {
    }

    public record StatusChangeRequest(@Size(max = 500) String reason) {
    }

    public record ReplaceRolesRequest(@NotEmpty @Size(max = 10) Set<@NotBlank String> roles) {
    }

    // ---- Department members -----------------------------------------------------------------------------------

    public record AddMemberRequest(@NotNull UUID userId, @Size(max = 100) String position) {
    }

    public record MemberResponse(UUID userId, String email, String fullName, String status, String position,
                                 Instant joinedAt) {
        static MemberResponse from(MemberView m) {
            return new MemberResponse(m.userId(), m.email(), m.fullName(), m.status(), m.position(), m.joinedAt());
        }
    }
}
