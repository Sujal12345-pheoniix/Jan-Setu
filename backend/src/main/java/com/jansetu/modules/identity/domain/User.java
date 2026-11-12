package com.jansetu.modules.identity.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20, updatable = false)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, String phone, UserType userType, Instant now,
                boolean passwordChangeRequired) {
        this.email = email.trim();
        this.emailNormalized = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.phone = phone;
        this.userType = userType;
        this.status = UserStatus.ACTIVE;
        this.passwordChangedAt = now;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ---- Login protection --------------------------------------------------------------------------------------

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Records a failed attempt; returns true if this attempt locked the account. */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    // ---- Credentials ------------------------------------------------------------------------------------------

    public void changePassword(String newHash, Instant now, boolean changeRequired) {
        this.passwordHash = newHash;
        this.passwordChangedAt = now;
        this.passwordChangeRequired = changeRequired;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Transparent hash upgrade (e.g. stronger work factor) after a successful login. */
    public void upgradePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    // ---- Account status ---------------------------------------------------------------------------------------

    public void changeStatus(UserStatus newStatus, String reason) {
        if (status == newStatus) {
            throw ApiException.invalidState("The account is already " + newStatus.name().toLowerCase(Locale.ROOT) + ".");
        }
        this.status = newStatus;
        this.statusReason = reason;
        if (newStatus == UserStatus.ACTIVE) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
    }

    public void updateProfile(String newFullName, String newPhone) {
        if (newFullName != null) {
            this.fullName = newFullName.trim();
        }
        if (newPhone != null) {
            this.phone = newPhone.isBlank() ? null : newPhone.trim();
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UserType getUserType() {
        return userType;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public String getStatusReason() {
        return statusReason;
    }
}
